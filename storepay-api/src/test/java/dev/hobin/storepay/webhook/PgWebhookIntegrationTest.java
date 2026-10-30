package dev.hobin.storepay.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hobin.storepay.catalog.application.ProductService;
import dev.hobin.storepay.catalog.application.dto.ProductResponse;
import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.merchant.application.MerchantService;
import dev.hobin.storepay.merchant.application.dto.MerchantResponse;
import dev.hobin.storepay.order.application.OrderService;
import dev.hobin.storepay.order.application.dto.OrderResponse;
import dev.hobin.storepay.payment.domain.Payment;
import dev.hobin.storepay.payment.domain.PaymentMethod;
import dev.hobin.storepay.payment.domain.PaymentRepository;
import dev.hobin.storepay.payment.domain.PaymentStatus;
import dev.hobin.storepay.payment.infrastructure.PgProperties;
import dev.hobin.storepay.store.application.StoreService;
import dev.hobin.storepay.store.application.dto.StoreResponse;
import dev.hobin.storepay.support.IntegrationTest;
import dev.hobin.storepay.webhook.application.PgWebhookService;
import dev.hobin.storepay.webhook.application.WebhookSignatureVerifier;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@DisplayName("결제사 웹훅")
class PgWebhookIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(700);

    @Autowired
    private MerchantService merchantService;
    @Autowired
    private StoreService storeService;
    @Autowired
    private ProductService productService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private PgWebhookService webhookService;
    @Autowired
    private WebhookSignatureVerifier signatureVerifier;
    @Autowired
    private PgProperties pgProperties;
    @Autowired
    private ObjectMapper objectMapper;

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

    /** 승인 요청은 결제사에 닿았지만 응답이 유실돼 READY 로 남은 결제를 만든다. */
    @Transactional
    Payment strandedPayment() {
        OrderResponse order = orderService.place(store.storeKey(),
                List.of(new OrderService.OrderLineCommand(coffee.productKey(), 1)));
        return paymentRepository.saveAndFlush(Payment.ready(
                orderService.getEntityByKey(order.orderKey()), PaymentMethod.CARD));
    }

    private String sign(long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    pgProperties.webhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String approvalBody(String paymentKey, String transactionId) {
        try {
            return objectMapper.writeValueAsString(new java.util.LinkedHashMap<String, Object>() {{
                put("eventType", "PAYMENT_APPROVED");
                put("paymentKey", paymentKey);
                put("transactionId", transactionId);
                put("amount", 5000);
                put("occurredAt", Instant.now().toString());
            }});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("유실된 승인 응답을 웹훅으로 복구한다")
    void reconcilesStrandedApproval() {
        Payment payment = strandedPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);

        String body = approvalBody(payment.getPaymentKey(), "pgtx_recovered");
        PgWebhookService.Result result = webhookService.handle(UUID.randomUUID().toString(), body);

        assertThat(result).isEqualTo(PgWebhookService.Result.RECONCILED);
        Payment reloaded = paymentRepository.findByPaymentKey(payment.getPaymentKey()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(reloaded.getPgTransactionId()).isEqualTo("pgtx_recovered");
    }

    @Test
    @DisplayName("같은 이벤트가 두 번 와도 한 번만 처리한다")
    void ignoresDuplicateEvent() {
        Payment payment = strandedPayment();
        String eventId = UUID.randomUUID().toString();
        String body = approvalBody(payment.getPaymentKey(), "pgtx_dup");

        assertThat(webhookService.handle(eventId, body)).isEqualTo(PgWebhookService.Result.RECONCILED);
        assertThat(webhookService.handle(eventId, body)).isEqualTo(PgWebhookService.Result.DUPLICATE);
    }

    @Test
    @DisplayName("이미 동기 응답으로 처리된 결제는 웹훅이 와도 건드리지 않는다")
    void leavesSettledPaymentAlone() {
        Payment payment = strandedPayment();
        String body = approvalBody(payment.getPaymentKey(), "pgtx_first");
        webhookService.handle(UUID.randomUUID().toString(), body);

        PgWebhookService.Result second = webhookService.handle(
                UUID.randomUUID().toString(), approvalBody(payment.getPaymentKey(), "pgtx_second"));

        assertThat(second).isEqualTo(PgWebhookService.Result.ALREADY_SETTLED);
        assertThat(paymentRepository.findByPaymentKey(payment.getPaymentKey()).orElseThrow()
                .getPgTransactionId()).isEqualTo("pgtx_first");
    }

    /**
     * 웹훅이 승인 트랜잭션 커밋보다 먼저 도착하는 경우. 조용히 삼키면 중복 방지 기록 때문에
     * 결제사의 재전송까지 DUPLICATE 로 걸러져 결제가 영원히 READY 로 남는다.
     */
    @Test
    @DisplayName("아직 보이지 않는 결제 건이면 거부하고, 재전송은 정상 처리된다")
    void rejectsUnknownPaymentSoItCanBeRetried() {
        String eventId = UUID.randomUUID().toString();
        String unknownBody = approvalBody("pay_아직없음", "pgtx_early");

        assertThatThrownBy(() -> webhookService.handle(eventId, unknownBody))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WEBHOOK_PAYMENT_NOT_READY);

        // 롤백됐으므로 같은 이벤트 ID 의 재전송이 DUPLICATE 로 막히지 않아야 한다.
        Payment payment = strandedPayment();
        String retryBody = approvalBody(payment.getPaymentKey(), "pgtx_retried");

        assertThat(webhookService.handle(eventId, retryBody))
                .isEqualTo(PgWebhookService.Result.RECONCILED);
        assertThat(paymentRepository.findByPaymentKey(payment.getPaymentKey()).orElseThrow()
                .getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    @DisplayName("서명이 틀리면 거부한다")
    void rejectsBadSignature() {
        String body = approvalBody("pay_whatever", "pgtx_x");
        long now = Instant.now().getEpochSecond();

        assertThatThrownBy(() -> signatureVerifier.verify("deadbeef", String.valueOf(now), body))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WEBHOOK_INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("본문이 한 글자라도 바뀌면 서명 검증에 실패한다")
    void detectsTamperedBody() {
        String body = approvalBody("pay_whatever", "pgtx_x");
        long now = Instant.now().getEpochSecond();
        String signature = sign(now, body);

        signatureVerifier.verify(signature, String.valueOf(now), body);

        String tampered = body.replace("5000", "1");
        assertThatThrownBy(() -> signatureVerifier.verify(signature, String.valueOf(now), tampered))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WEBHOOK_INVALID_SIGNATURE);
    }

    @Test
    @DisplayName("허용 시간을 벗어난 타임스탬프는 서명이 맞아도 거부한다")
    void rejectsStaleTimestamp() {
        String body = approvalBody("pay_whatever", "pgtx_x");
        long stale = Instant.now().minusSeconds(pgProperties.webhookTolerance().toSeconds() + 60)
                .getEpochSecond();
        String signature = sign(stale, body);

        assertThatThrownBy(() -> signatureVerifier.verify(signature, String.valueOf(stale), body))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WEBHOOK_STALE_TIMESTAMP);
    }
}
