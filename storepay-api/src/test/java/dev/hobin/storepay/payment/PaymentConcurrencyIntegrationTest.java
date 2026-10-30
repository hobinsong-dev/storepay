package dev.hobin.storepay.payment;

import static org.assertj.core.api.Assertions.assertThat;

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
import dev.hobin.storepay.payment.domain.PaymentStatus;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.support.Concurrently;
import dev.hobin.storepay.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 결제 동시성.
 *
 * <p>"멱등키를 넣었다"와 "중복 승인이 막힌다"는 다른 말이다. 단일 스레드 테스트는 전자만 증명한다.
 * 손님 카드가 두 번 긁히는 사고는 전부 경합 구간에서 일어나므로, 실제로 스레드를 겹쳐 확인한다.
 *
 * <p>방어선은 세 겹이고 각각 막는 경우가 다르다.
 * <ol>
 *   <li>멱등키 유니크 제약 — 같은 키로 들어온 재시도</li>
 *   <li>{@code existsByOrder} 선검사 — 이미 끝난 결제에 대한 새 요청</li>
 *   <li>{@code uk_payment_order} 유니크 제약 — 선검사를 둘 다 통과한 동시 요청</li>
 * </ol>
 */
@DisplayName("결제 동시성")
class PaymentConcurrencyIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(800);
    private static final int THREADS = 16;

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
    private JdbcTemplate jdbcTemplate;

    private String storeKey;
    private String productKey;

    @BeforeEach
    void setUp() {
        MerchantResponse merchant = merchantService.register(
                "호빈상사", "%03d-45-67890".formatted(SEQUENCE.incrementAndGet()));
        merchantService.activate(merchant.merchantKey());
        storeKey = storeService.open(merchant.merchantKey(), "구로점", "서울 구로구").storeKey();
        productKey = productService.create(storeKey, "아메리카노", new BigDecimal("5000")).productKey();
    }

    private OrderResponse placeOrder(int quantity) {
        return orderService.place(storeKey,
                List.of(new OrderService.OrderLineCommand(productKey, quantity)));
    }

    private int countPayments(String orderKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment p JOIN orders o ON o.id = p.order_id WHERE o.order_key = ?",
                Integer.class, orderKey);
        return count == null ? 0 : count;
    }

    /**
     * POS 단말이 응답을 못 받고 재시도할 때, 느린 네트워크에서는 원 요청과 재시도가 겹쳐 도착한다.
     * 두 요청 모두 선점을 통과하면 같은 주문에 결제가 두 건 생긴다.
     */
    @Test
    @DisplayName("같은 멱등키로 동시에 승인해도 결제는 한 건만 생긴다")
    void singlePaymentForSameIdempotencyKey() {
        OrderResponse order = placeOrder(2);
        String idempotencyKey = UUID.randomUUID().toString();

        List<Concurrently.Outcome<PaymentResponse>> outcomes = Concurrently.run(THREADS,
                () -> paymentService.approve(idempotencyKey, order.orderKey(), PaymentMethod.CARD));

        assertThat(countPayments(order.orderKey())).isEqualTo(1);

        List<PaymentResponse> approved = outcomes.stream()
                .filter(Concurrently.Outcome::succeeded)
                .map(Concurrently.Outcome::value)
                .toList();
        assertThat(approved).isNotEmpty();
        assertThat(approved).extracting(PaymentResponse::paymentKey).containsOnly(approved.get(0).paymentKey());
        assertThat(approved).extracting(PaymentResponse::status).containsOnly(PaymentStatus.APPROVED);

        // 진 쪽은 끝까지 밀고 들어가는 대신 "처리 중"을 받는다. 500 이 아니라 재시도 가능한 응답이어야 한다.
        assertThat(outcomes.stream().filter(o -> !o.succeeded()))
                .allSatisfy(o -> assertThat(o.error())
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.IDEMPOTENCY_IN_PROGRESS));
    }

    /**
     * 멱등키가 다르면 1번 방어선은 통과한다. 단말을 바꿔 다시 긁거나, 재시도마다 키를 새로 만드는
     * 잘못 구현된 클라이언트에서 실제로 일어난다. 이때는 주문 유니크 제약이 마지막 방어선이다.
     */
    @Test
    @DisplayName("멱등키가 달라도 같은 주문에는 결제가 한 건만 생긴다")
    void singlePaymentForSameOrder() {
        OrderResponse order = placeOrder(1);

        List<Concurrently.Outcome<PaymentResponse>> outcomes = Concurrently.run(THREADS,
                () -> paymentService.approve(
                        UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD));

        assertThat(countPayments(order.orderKey())).isEqualTo(1);

        long succeeded = outcomes.stream().filter(Concurrently.Outcome::succeeded).count();
        assertThat(succeeded).isEqualTo(1);

        // 유니크 제약 위반이 그대로 새어 나가면 500 이 된다. 409 로 번역돼야 한다.
        assertThat(outcomes.stream().filter(o -> !o.succeeded()))
                .allSatisfy(o -> assertThat(o.error())
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PAYMENT_ALREADY_EXISTS));
    }

    /**
     * 10,000원 결제에 6,000원 취소를 두 번 동시에 넣으면 12,000원이 환불될 수 있다.
     * 잔액 검사는 각 트랜잭션이 자기가 읽은 스냅샷으로 하기 때문에 둘 다 통과한다.
     */
    @Test
    @DisplayName("동시 부분취소는 취소 가능 잔액을 넘지 못한다")
    void concurrentCancelsCannotExceedBalance() {
        OrderResponse order = placeOrder(2);
        PaymentResponse payment = paymentService.approve(
                UUID.randomUUID().toString(), order.orderKey(), PaymentMethod.CARD);

        List<Concurrently.Outcome<PaymentResponse>> outcomes = Concurrently.run(2,
                () -> paymentService.cancel(payment.paymentKey(), new BigDecimal("6000"), "고객 변심"));

        long succeeded = outcomes.stream().filter(Concurrently.Outcome::succeeded).count();
        assertThat(succeeded).isEqualTo(1);

        BigDecimal canceled = jdbcTemplate.queryForObject(
                "SELECT canceled_amount FROM payment WHERE payment_key = ?",
                BigDecimal.class, payment.paymentKey());
        assertThat(canceled).isEqualByComparingTo("6000");

        Integer cancelRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_cancel c JOIN payment p ON p.id = c.payment_id"
                        + " WHERE p.payment_key = ?",
                Integer.class, payment.paymentKey());
        assertThat(cancelRows).isEqualTo(1);
    }
}
