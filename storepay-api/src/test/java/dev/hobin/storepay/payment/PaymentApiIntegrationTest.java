package dev.hobin.storepay.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.catalog.application.dto.ProductResponse;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.merchant.application.dto.MerchantResponse;
import dev.hobin.storepay.order.application.OrderService;
import dev.hobin.storepay.order.application.dto.OrderResponse;
import dev.hobin.storepay.order.domain.OrderStatus;
import dev.hobin.storepay.payment.application.PaymentService;
import dev.hobin.storepay.payment.application.dto.PaymentResponse;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentStatus;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.store.application.dto.StoreResponse;
import dev.hobin.storepay.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("결제 통합")
class PaymentApiIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(500);

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

    private StoreResponse store;
    private ProductResponse coffee;

    @BeforeEach
    void setUp() {
        MerchantResponse merchant = merchantService.register(
                "호빈상사", "%03d-45-67890".formatted(SEQUENCE.incrementAndGet()));
        merchantService.activate(merchant.merchantKey());
        store = storeService.open(merchant.merchantKey(), "구로점", "서울 구로구");
        coffee = productService.create(store.storeKey(), "아메리카노", new BigDecimal("5000"));
    }

    private OrderResponse placeOrder(int quantity) {
        return orderService.place(store.storeKey(),
                List.of(new OrderService.OrderLineCommand(coffee.productKey(), quantity)));
    }

    @Test
    @DisplayName("승인하면 결제와 주문 상태가 함께 바뀐다")
    void approves() {
        OrderResponse order = placeOrder(2);
        PaymentResponse payment = paymentService.approve(
                UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD);

        assertThat(payment.status()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.totalAmount()).isEqualByComparingTo("10000");
        assertThat(payment.pgTransactionId()).startsWith("pgtx_");
        assertThat(orderService.getByKey(order.orderKey()).status()).isEqualTo(OrderStatus.PAID);
    }

    /**
     * POS 단말은 응답을 못 받으면 같은 요청을 다시 보낸다.
     * 두 번째 요청이 새 결제를 만들면 손님 카드가 두 번 긁힌다.
     */
    @Test
    @DisplayName("같은 Idempotency-Key 로 다시 호출하면 처음 결제를 그대로 돌려준다")
    void replaysOnSameIdempotencyKey() {
        OrderResponse order = placeOrder(1);
        String key = UUID.randomUUID().toString();

        PaymentResponse first = paymentService.approve(key, order.orderKey(), PaymentMethod.CARD);
        PaymentResponse second = paymentService.approve(key, order.orderKey(), PaymentMethod.CARD);

        assertThat(second.paymentKey()).isEqualTo(first.paymentKey());
        assertThat(second.pgTransactionId()).isEqualTo(first.pgTransactionId());
    }

    @Test
    @DisplayName("같은 키에 다른 내용을 보내면 거부한다")
    void rejectsSameKeyWithDifferentPayload() {
        OrderResponse order = placeOrder(1);
        String key = UUID.randomUUID().toString();
        paymentService.approve(key, order.orderKey(), PaymentMethod.CARD);

        OrderResponse another = placeOrder(1);
        assertThatThrownBy(() -> paymentService.approve(key, another.orderKey(), PaymentMethod.CARD))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @Test
    @DisplayName("키가 달라도 이미 결제된 주문은 다시 결제할 수 없다")
    void rejectsSecondPaymentForSameOrder() {
        OrderResponse order = placeOrder(1);
        paymentService.approve(UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD);

        assertThatThrownBy(() -> paymentService.approve(
                UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS);
    }

    /** 실패한 요청이 키를 붙잡고 있으면 손님은 영원히 다시 결제할 수 없다. */
    @Test
    @DisplayName("실패한 요청의 멱등 키는 풀려서 같은 키로 재시도할 수 있다")
    void releasesKeyOnFailure() {
        String key = UUID.randomUUID().toString();

        assertThatThrownBy(() -> paymentService.approve(key, "ord_존재하지않음", PaymentMethod.CARD))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);

        OrderResponse order = placeOrder(1);
        PaymentResponse retried = paymentService.approve(key, order.orderKey(), PaymentMethod.CARD);
        assertThat(retried.status()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("PG 가 거절하면 결제는 FAILED 로 남고 사유가 보존된다")
    void keepsFailureReason() {
        // SimulatedPgClient 는 금액 끝자리가 .99 면 한도 초과로 거절한다.
        ProductResponse odd = productService.create(store.storeKey(), "거절유발상품", new BigDecimal("1000.99"));
        OrderResponse order = orderService.place(store.storeKey(),
                List.of(new OrderService.OrderLineCommand(odd.productKey(), 1)));

        PaymentResponse payment = paymentService.approve(
                UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD);

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureReason()).isEqualTo("한도 초과");
        assertThat(orderService.getByKey(order.orderKey()).status()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("부분 취소 후 잔액을 마저 취소하면 주문까지 취소된다")
    void partialThenFullCancel() {
        OrderResponse order = placeOrder(2);
        PaymentResponse payment = paymentService.approve(
                UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD);

        PaymentResponse partial = paymentService.cancel(
                payment.paymentKey(), new BigDecimal("3000"), "일부 환불");
        assertThat(partial.status()).isEqualTo(PaymentStatus.PARTIAL_CANCELED);
        assertThat(partial.cancelableAmount()).isEqualByComparingTo("7000");

        PaymentResponse full = paymentService.cancel(
                payment.paymentKey(), new BigDecimal("7000"), "전액 환불");
        assertThat(full.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(full.cancels()).hasSize(2);
        assertThat(orderService.getByKey(order.orderKey()).status()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    @DisplayName("취소 가능 잔액을 넘으면 PG 를 부르기 전에 거부한다")
    void rejectsOverCancel() {
        OrderResponse order = placeOrder(1);
        PaymentResponse payment = paymentService.approve(
                UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD);

        assertThatThrownBy(() -> paymentService.cancel(
                payment.paymentKey(), new BigDecimal("9999"), "초과 요청"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PAYMENT_INVALID_CANCEL_AMOUNT);

        assertThat(paymentService.getByKey(payment.paymentKey()).status())
                .isEqualTo(PaymentStatus.APPROVED);
    }
}
