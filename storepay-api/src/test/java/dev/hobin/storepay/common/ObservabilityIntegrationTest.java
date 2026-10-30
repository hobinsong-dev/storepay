package dev.hobin.storepay.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.merchant.application.dto.MerchantResponse;
import dev.hobin.storepay.order.application.OrderService;
import dev.hobin.storepay.order.application.dto.OrderResponse;
import dev.hobin.storepay.payment.application.PaymentService;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.MeterNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 지표 수집.
 *
 * <p>"메트릭을 붙였다"와 "알림을 걸 수 있다"는 다른 말이다. 승인 거절과 승인 실패가
 * 같은 카운터로 들어가면 한도 초과가 늘어난 것인지 장애인지 구분할 수 없어,
 * 결국 아무도 그 알림을 보지 않게 된다. 그래서 태그가 갈리는지까지 확인한다.
 */
@DisplayName("지표 수집 통합")
class ObservabilityIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    /** 승인 거절을 유도하는 약속. 금액 끝자리가 .99 면 모의 결제사가 한도 초과로 거절한다. */
    private static final BigDecimal DECLINE_PRICE = new BigDecimal("9999.99");

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
    private MeterRegistry meterRegistry;

    private String storeKey;

    @BeforeEach
    void setUp() {
        MerchantResponse merchant = merchantService.register(
                "호빈상사", "%03d-45-67890".formatted(SEQUENCE.incrementAndGet()));
        merchantService.activate(merchant.merchantKey());
        storeKey = storeService.open(merchant.merchantKey(), "구로점", "서울 구로구").storeKey();
    }

    private OrderResponse placeOrder(BigDecimal unitPrice) {
        String productKey = productService.create(storeKey, "아메리카노", unitPrice).productKey();
        return orderService.place(storeKey,
                List.of(new OrderService.OrderLineCommand(productKey, 1)));
    }

    private double approveCount(String outcome) {
        return meterRegistry.find("storepay.payment.approve")
                .tag("outcome", outcome)
                .counters().stream()
                .mapToDouble(counter -> counter.count())
                .sum();
    }

    @Test
    @DisplayName("승인 성공은 method 와 outcome 태그로 집계된다")
    void countsApprovals() {
        double before = approveCount("approved");

        paymentService.approve(UUID.randomUUID().toString(),
                placeOrder(new BigDecimal("5000")).orderKey(), PaymentMethod.CARD);

        assertThat(approveCount("approved")).isEqualTo(before + 1);
        assertThat(meterRegistry.get("storepay.payment.approve")
                .tag("method", "CARD").tag("outcome", "approved").counter()).isNotNull();
    }

    /** 결제사가 정상 응답으로 거절한 것은 장애가 아니다. 장애 알림에 섞이면 알림이 무의미해진다. */
    @Test
    @DisplayName("결제사 거절은 실패가 아니라 declined 로 분리된다")
    void separatesDeclineFromFailure() {
        double declinedBefore = approveCount("declined");
        double failedBefore = approveCount("failed");

        paymentService.approve(UUID.randomUUID().toString(),
                placeOrder(DECLINE_PRICE).orderKey(), PaymentMethod.CARD);

        assertThat(approveCount("declined")).isEqualTo(declinedBefore + 1);
        assertThat(approveCount("failed")).isEqualTo(failedBefore);
    }

    @Test
    @DisplayName("우리 쪽 규칙으로 막힌 요청은 failed 로 에러 코드와 함께 집계된다")
    void countsFailures() {
        double before = approveCount("failed");

        assertThatThrownBy(() -> paymentService.approve(
                UUID.randomUUID().toString(), "ord_not_exist", PaymentMethod.CARD))
                .isInstanceOf(RuntimeException.class);

        assertThat(approveCount("failed")).isEqualTo(before + 1);
        assertThat(meterRegistry.get("storepay.payment.approve")
                .tag("outcome", "failed").tag("error", "ORDER_404").counter().count())
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("결제사 호출 시간은 operation 과 outcome 으로 나뉘어 기록된다")
    void recordsPgCallTiming() {
        paymentService.approve(UUID.randomUUID().toString(),
                placeOrder(new BigDecimal("5000")).orderKey(), PaymentMethod.CARD);

        Timer timer = meterRegistry.get("storepay.pg.call")
                .tag("operation", "approve").tag("outcome", "success").timer();

        assertThat(timer.count()).isGreaterThanOrEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS)).isPositive();
    }

    /**
     * 태그에 건별로 유일한 값이 들어가면 거래 수만큼 시계열이 생겨 수집기가 먼저 죽는다.
     * 건별 추적은 로그(MDC)의 일이다.
     */
    @Test
    @DisplayName("지표 태그에 결제 키 같은 고유 값은 들어가지 않는다")
    void doesNotTagHighCardinalityValues() {
        paymentService.approve(UUID.randomUUID().toString(),
                placeOrder(new BigDecimal("5000")).orderKey(), PaymentMethod.CARD);

        assertThat(meterRegistry.find("storepay.payment.approve").meters())
                .isNotEmpty()
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .extracting(tag -> tag.getKey())
                        .doesNotContain("paymentKey", "orderKey", "idempotencyKey"));
    }

    @Test
    @DisplayName("취소 지표도 결제사 호출과 함께 기록된다")
    void countsCancels() {
        String paymentKey = paymentService.approve(UUID.randomUUID().toString(),
                placeOrder(new BigDecimal("5000")).orderKey(), PaymentMethod.CARD).paymentKey();

        paymentService.cancel(paymentKey, new BigDecimal("5000"), "고객 변심");

        assertThat(meterRegistry.get("storepay.payment.cancel")
                .tag("outcome", "canceled").counter().count()).isGreaterThanOrEqualTo(1);
        assertThat(meterRegistry.get("storepay.pg.call")
                .tag("operation", "cancel").timer().count()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("성공하지 않은 결제사 호출은 success 타이머에 섞이지 않는다")
    void keepsOutcomesSeparate() {
        paymentService.approve(UUID.randomUUID().toString(),
                placeOrder(new BigDecimal("5000")).orderKey(), PaymentMethod.CARD);

        // 실패 타이머는 아직 만들어진 적이 없어야 한다 (모의 결제사는 예외를 던지지 않는다).
        assertThatThrownBy(() -> meterRegistry.get("storepay.pg.call")
                .tag("operation", "approve").tag("outcome", "timeout").timer())
                .isInstanceOf(MeterNotFoundException.class);
    }
}
