package dev.hobin.mockpg;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/payments")
public class PgController {

    /** 승인 거절을 재현하기 위한 약속. 금액 끝자리가 .99 면 한도 초과로 거절한다. */
    private static final BigDecimal DECLINE_MARKER = new BigDecimal("0.99");

    private final MockPgProperties properties;
    private final WebhookSender webhookSender;

    @PostMapping("/approve")
    public ResponseEntity<?> approve(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ApproveRequest request) {

        if (!properties.apiKey().equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody("invalid api key"));
        }
        sleepLatency();

        if (request.amount().remainder(BigDecimal.ONE).compareTo(DECLINE_MARKER) == 0) {
            log.info("승인 거절: paymentKey={}, amount={}", request.paymentKey(), request.amount());
            return ResponseEntity.ok(new ApproveResponse(false, null, null, "한도 초과"));
        }

        String transactionId = "pgtx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Instant approvedAt = Instant.now();
        log.info("승인: paymentKey={}, transactionId={}", request.paymentKey(), transactionId);

        webhookSender.send("PAYMENT_APPROVED", request.paymentKey(), transactionId, request.amount(), approvedAt);
        return ResponseEntity.ok(new ApproveResponse(true, transactionId, approvedAt, null));
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancel(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestBody CancelRequest request) {

        if (!properties.apiKey().equals(apiKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorBody("invalid api key"));
        }
        String cancelId = "pgcl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("취소: transactionId={}, amount={}", request.transactionId(), request.amount());
        return ResponseEntity.ok(new CancelResponse(cancelId, Instant.now()));
    }

    private void sleepLatency() {
        long millis = properties.approveLatency().toMillis();
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(millis / 2 + 1, millis + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record ApproveRequest(String paymentKey, String orderKey, String method, BigDecimal amount) {
    }

    record ApproveResponse(boolean approved, String transactionId, Instant approvedAt, String failureReason) {
    }

    record CancelRequest(String transactionId, BigDecimal amount, String reason) {
    }

    record CancelResponse(String cancelId, Instant canceledAt) {
    }

    record ErrorBody(String message) {
    }
}
