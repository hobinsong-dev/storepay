package dev.hobin.storepay.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.merchant.application.dto.MerchantResponse;
import dev.hobin.storepay.order.application.OrderService;
import dev.hobin.storepay.order.application.dto.OrderResponse;
import dev.hobin.storepay.payment.application.PaymentService;
import dev.hobin.storepay.payment.application.dto.PaymentResponse;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.settlement.application.BusinessDay;
import dev.hobin.storepay.settlement.application.ReconciliationService;
import dev.hobin.storepay.settlement.application.SettlementService;
import dev.hobin.storepay.settlement.application.dto.ReconciliationResponse;
import dev.hobin.storepay.settlement.application.dto.SettlementResponse;
import dev.hobin.storepay.settlement.domain.MismatchType;
import dev.hobin.storepay.settlement.domain.PgSettlementClient;
import dev.hobin.storepay.settlement.domain.ReconciliationStatus;
import dev.hobin.storepay.settlement.domain.SettlementStatus;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.support.IntegrationTest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 대사와 정산 통합.
 *
 * <p>시계와 결제사 거래내역을 둘 다 가짜로 끼운다. 시계를 고정하지 않으면 "끝난 영업일만 대사한다"는
 * 규칙 때문에 오늘 만든 결제를 영원히 대사할 수 없고, 거래내역을 통제하지 않으면
 * 불일치 상황 자체를 만들 수 없다.
 */
@DisplayName("대사·정산 통합")
class SettlementIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(900);

    @Autowired
    private MerchantService merchantService;
    @Autowired
    private StoreService storeService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private ReconciliationService reconciliationService;
    @Autowired
    private SettlementService settlementService;
    @Autowired
    private BusinessDay businessDay;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private Clock clock;
    @MockitoBean
    private PgSettlementClient pgSettlementClient;

    private String merchantKey;
    private String storeKey;
    private String productKey;
    private LocalDate businessDate;

    /**
     * 대사와 정산은 "그 영업일의 모든 거래"를 집계한다. 다른 테스트가 남긴 결제가 같은 창에 들어 있으면
     * 이 테스트가 만든 상황이 아니라 누적된 상황을 검증하게 된다. 그래서 매 테스트마다 비운다
     * (집계 대상이 전역인 기능은 트랜잭션 롤백으로는 격리되지 않는다 —
     * 승인 자체가 독립 트랜잭션으로 커밋되기 때문이다).
     */
    @BeforeEach
    void clearLedger() {
        jdbcTemplate.execute("""
                TRUNCATE settlement, reconciliation_mismatch, reconciliation_run,
                         payment_cancel, payment, order_item, orders
                RESTART IDENTITY CASCADE""");
    }

    @BeforeEach
    void setUp() {
        MerchantResponse merchant = merchantService.register(
                "호빈상사", "%03d-45-67890".formatted(SEQUENCE.incrementAndGet()));
        merchantService.activate(merchant.merchantKey());
        merchantKey = merchant.merchantKey();
        storeKey = storeService.open(merchantKey, "구로점", "서울 구로구").storeKey();
        productKey = productService.create(storeKey, "아메리카노", new BigDecimal("5000")).productKey();

        // 결제는 실제 지금 시각으로 승인된다. 그 영업일을 대사하려면 시계가 하루 넘어가 있어야 한다.
        businessDate = businessDay.today(Instant.now());
        given(clock.instant()).willReturn(businessDay.endOf(businessDate).plusSeconds(3600));
    }

    private PaymentResponse approve(int quantity, PaymentMethod method) {
        OrderResponse order = orderService.place(storeKey,
                List.of(new OrderService.OrderLineCommand(productKey, quantity)));
        return paymentService.approve(UUID.randomUUID().toString(), order.orderKey(), method);
    }

    /** 결제사가 우리 장부를 그대로 되비추는, 즉 불일치가 없는 경우. */
    private void pgMirrors(PaymentResponse... payments) {
        List<PgSettlementClient.PgTransaction> transactions = new ArrayList<>();
        for (PaymentResponse payment : payments) {
            transactions.add(new PgSettlementClient.PgTransaction(
                    payment.paymentKey(), payment.pgTransactionId(),
                    payment.totalAmount().subtract(payment.canceledAmount()),
                    payment.status().name()));
        }
        given(pgSettlementClient.fetchTransactions(any())).willReturn(transactions);
    }

    @Nested
    @DisplayName("대사")
    class Reconciling {

        @Test
        @DisplayName("양쪽 장부가 같으면 불일치 0건으로 끝난다")
        void clean() {
            PaymentResponse first = approve(2, PaymentMethod.CARD);
            PaymentResponse second = approve(1, PaymentMethod.EASY_PAY);
            pgMirrors(first, second);

            ReconciliationResponse result = reconciliationService.reconcile(businessDate);

            assertThat(result.status()).isEqualTo(ReconciliationStatus.COMPLETED);
            assertThat(result.internalCount()).isEqualTo(2);
            assertThat(result.pgCount()).isEqualTo(2);
            assertThat(result.matchedCount()).isEqualTo(2);
            assertThat(result.mismatchCount()).isZero();
        }

        @Test
        @DisplayName("결제사 내역에 없는 건은 MISSING_IN_PG 로 잡는다")
        void missingInPg() {
            PaymentResponse kept = approve(1, PaymentMethod.CARD);
            PaymentResponse dropped = approve(2, PaymentMethod.CARD);
            pgMirrors(kept);

            ReconciliationResponse result = reconciliationService.reconcile(businessDate);

            assertThat(result.mismatchCount()).isEqualTo(1);
            assertThat(result.mismatches()).singleElement().satisfies(mismatch -> {
                assertThat(mismatch.type()).isEqualTo(MismatchType.MISSING_IN_PG);
                assertThat(mismatch.paymentKey()).isEqualTo(dropped.paymentKey());
                assertThat(mismatch.internalAmount()).isEqualByComparingTo("10000");
            });
        }

        /** 승인 응답도 웹훅도 모두 놓친 경우. 손님 카드는 긁혔는데 우리 매출이 없다. */
        @Test
        @DisplayName("우리에게 없는 건은 MISSING_IN_INTERNAL 로 잡는다")
        void missingInInternal() {
            PaymentResponse known = approve(1, PaymentMethod.CARD);
            given(pgSettlementClient.fetchTransactions(any())).willReturn(List.of(
                    new PgSettlementClient.PgTransaction(
                            known.paymentKey(), known.pgTransactionId(),
                            known.totalAmount(), known.status().name()),
                    new PgSettlementClient.PgTransaction(
                            "pay_unknown_0001", "pgtx_unknown", new BigDecimal("7000"), "APPROVED")));

            ReconciliationResponse result = reconciliationService.reconcile(businessDate);

            assertThat(result.mismatches()).singleElement().satisfies(mismatch -> {
                assertThat(mismatch.type()).isEqualTo(MismatchType.MISSING_IN_INTERNAL);
                assertThat(mismatch.pgAmount()).isEqualByComparingTo("7000");
            });
        }

        @Test
        @DisplayName("금액이 다르면 양쪽 값을 모두 남긴다")
        void amountMismatch() {
            PaymentResponse payment = approve(2, PaymentMethod.CARD);
            given(pgSettlementClient.fetchTransactions(any())).willReturn(List.of(
                    new PgSettlementClient.PgTransaction(
                            payment.paymentKey(), payment.pgTransactionId(),
                            new BigDecimal("9000"), payment.status().name())));

            ReconciliationResponse result = reconciliationService.reconcile(businessDate);

            assertThat(result.mismatches()).singleElement().satisfies(mismatch -> {
                assertThat(mismatch.type()).isEqualTo(MismatchType.AMOUNT_MISMATCH);
                assertThat(mismatch.internalAmount()).isEqualByComparingTo("10000");
                assertThat(mismatch.pgAmount()).isEqualByComparingTo("9000");
            });
        }

        /** BigDecimal 의 equals 로 비교하면 10000 과 10000.00 이 달라 매일 전 건이 불일치로 잡힌다. */
        @Test
        @DisplayName("scale 이 달라도 같은 금액이면 일치로 본다")
        void ignoresScale() {
            PaymentResponse payment = approve(2, PaymentMethod.CARD);
            given(pgSettlementClient.fetchTransactions(any())).willReturn(List.of(
                    new PgSettlementClient.PgTransaction(
                            payment.paymentKey(), payment.pgTransactionId(),
                            new BigDecimal("10000.0000"), payment.status().name())));

            assertThat(reconciliationService.reconcile(businessDate).mismatchCount()).isZero();
        }

        @Test
        @DisplayName("결제사를 못 부르면 불일치 0건이 아니라 FAILED 로 남는다")
        void pgUnavailable() {
            approve(1, PaymentMethod.CARD);
            given(pgSettlementClient.fetchTransactions(any()))
                    .willThrow(new BusinessException(ErrorCode.RECONCILIATION_PG_UNAVAILABLE));

            ReconciliationResponse result = reconciliationService.reconcile(businessDate);

            assertThat(result.status()).isEqualTo(ReconciliationStatus.FAILED);
            assertThat(result.failureReason()).isNotBlank();
        }

        @Test
        @DisplayName("다시 실행하면 이전 결과를 대체한다")
        void rerunReplaces() {
            PaymentResponse payment = approve(1, PaymentMethod.CARD);
            given(pgSettlementClient.fetchTransactions(any())).willReturn(List.of());

            ReconciliationResponse first = reconciliationService.reconcile(businessDate);
            assertThat(first.mismatchCount()).isEqualTo(1);

            pgMirrors(payment);
            ReconciliationResponse second = reconciliationService.reconcile(businessDate);

            assertThat(second.runKey()).isNotEqualTo(first.runKey());
            assertThat(second.mismatchCount()).isZero();
            assertThat(reconciliationService.getByDate(businessDate).runKey()).isEqualTo(second.runKey());
        }

        @Test
        @DisplayName("아직 끝나지 않은 영업일은 거부한다")
        void rejectsOpenBusinessDay() {
            assertThatThrownBy(() -> reconciliationService.reconcile(businessDate.plusDays(5)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RECONCILIATION_FUTURE_DATE);
        }
    }

    @Nested
    @DisplayName("정산")
    class Settling {

        @Test
        @DisplayName("승인 합계에서 취소와 수수료를 빼 지급액을 낸다")
        void aggregates() {
            PaymentResponse card = approve(2, PaymentMethod.CARD);          // 10,000, 수수료 230
            PaymentResponse easyPay = approve(1, PaymentMethod.EASY_PAY);   // 5,000, 수수료 140
            pgMirrors(card, easyPay);
            reconciliationService.reconcile(businessDate);

            List<SettlementResponse> settlements = settlementService.close(businessDate);

            assertThat(settlements).singleElement().satisfies(settlement -> {
                assertThat(settlement.merchantKey()).isEqualTo(merchantKey);
                assertThat(settlement.transactionCount()).isEqualTo(2);
                assertThat(settlement.approvedAmount()).isEqualByComparingTo("15000");
                assertThat(settlement.canceledAmount()).isEqualByComparingTo("0");
                assertThat(settlement.feeAmount()).isEqualByComparingTo("370");
                assertThat(settlement.payoutAmount()).isEqualByComparingTo("14630");
                assertThat(settlement.status()).isEqualTo(SettlementStatus.PENDING);
            });
        }

        /** 취소하면 그 건의 수수료도 함께 돌려준다. 돌려주지 않으면 팔지도 않은 매출의 수수료를 떠안는다. */
        @Test
        @DisplayName("취소분은 금액과 수수료를 함께 차감한다")
        void deductsCancels() {
            PaymentResponse card = approve(2, PaymentMethod.CARD);
            paymentService.cancel(card.paymentKey(), new BigDecimal("4000"), "일부 환불");
            pgMirrors(paymentService.getByKey(card.paymentKey()));
            reconciliationService.reconcile(businessDate);

            List<SettlementResponse> settlements = settlementService.close(businessDate);

            assertThat(settlements).singleElement().satisfies(settlement -> {
                assertThat(settlement.approvedAmount()).isEqualByComparingTo("10000");
                assertThat(settlement.canceledAmount()).isEqualByComparingTo("4000");
                // 230 - (4000 * 0.023 = 92) = 138
                assertThat(settlement.feeAmount()).isEqualByComparingTo("138");
                assertThat(settlement.payoutAmount()).isEqualByComparingTo("5862");
            });
        }

        @Test
        @DisplayName("대사가 없으면 정산할 수 없다")
        void requiresReconciliation() {
            approve(1, PaymentMethod.CARD);

            assertThatThrownBy(() -> settlementService.close(businessDate))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SETTLEMENT_RECONCILIATION_REQUIRED);
        }

        @Test
        @DisplayName("불일치가 남아 있으면 정산할 수 없다")
        void requiresCleanReconciliation() {
            approve(1, PaymentMethod.CARD);
            given(pgSettlementClient.fetchTransactions(any())).willReturn(List.of());
            reconciliationService.reconcile(businessDate);

            assertThatThrownBy(() -> settlementService.close(businessDate))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SETTLEMENT_RECONCILIATION_REQUIRED);
        }

        /** 같은 날짜를 두 번 돌리는 것만으로 돈이 두 번 나가면 안 된다. */
        @Test
        @DisplayName("이미 마감된 영업일은 다시 마감할 수 없다")
        void rejectsDoubleClose() {
            pgMirrors(approve(1, PaymentMethod.CARD));
            reconciliationService.reconcile(businessDate);
            settlementService.close(businessDate);

            assertThatThrownBy(() -> settlementService.close(businessDate))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SETTLEMENT_ALREADY_CLOSED);
        }

        @Test
        @DisplayName("지급 완료 처리는 한 번만 된다")
        void payoutIsOnce() {
            pgMirrors(approve(1, PaymentMethod.CARD));
            reconciliationService.reconcile(businessDate);
            String settlementKey = settlementService.close(businessDate).get(0).settlementKey();

            SettlementResponse paid = settlementService.markPaid(settlementKey);
            assertThat(paid.status()).isEqualTo(SettlementStatus.PAID);
            assertThat(paid.paidAt()).isNotNull();

            assertThatThrownBy(() -> settlementService.markPaid(settlementKey))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SETTLEMENT_ALREADY_PAID);
        }

        @Test
        @DisplayName("거래가 없는 날은 빈 정산을 남기지 않는다")
        void noTransactions() {
            given(pgSettlementClient.fetchTransactions(any())).willReturn(List.of());
            reconciliationService.reconcile(businessDate);

            assertThat(settlementService.close(businessDate)).isEmpty();
        }
    }
}
