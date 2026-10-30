package dev.hobin.storepay.payment.infrastructure;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.payment.domain.PgClient;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * 실제 결제사(HTTP) 어댑터.
 *
 * <p>재시도는 <b>타임아웃·연결 실패에만</b> 건다. 4xx 응답은 결제사가 요청을 보고 거절한 것이라
 * 다시 보내도 같은 답이 오고, 5xx 는 승인이 이미 처리됐는데 응답만 못 받았을 수 있어
 * 그대로 재시도하면 이중 결제가 된다. 멱등키를 함께 보내는 이유도 같다.
 */
@Slf4j
@Component
@Profile("pg-http")
public class HttpPgClient implements PgClient {

    private final RestClient restClient;
    private final PgProperties properties;

    public HttpPgClient(RestClient.Builder builder, PgProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("X-Api-Key", properties.apiKey())
                .build();
    }

    @Override
    public ApproveResult approve(ApproveCommand command) {
        PgApproveResponse response = callWithRetry("approve", () -> restClient.post()
                .uri("/v1/payments/approve")
                // 결제사 쪽에서도 중복을 걸러낼 수 있도록 우리 결제 키를 멱등키로 넘긴다.
                .header("Idempotency-Key", command.paymentKey())
                .body(new PgApproveRequest(
                        command.paymentKey(), command.orderKey(),
                        command.method().name(), command.amount()))
                .retrieve()
                .body(PgApproveResponse.class));

        if (response == null) {
            throw new BusinessException(ErrorCode.PAYMENT_PG_UNAVAILABLE, "결제사 응답이 비어 있습니다.");
        }
        return response.approved()
                ? ApproveResult.approved(response.transactionId(), response.approvedAt())
                : ApproveResult.declined(response.failureReason());
    }

    @Override
    public CancelResult cancel(CancelCommand command) {
        PgCancelResponse response = callWithRetry("cancel", () -> restClient.post()
                .uri("/v1/payments/cancel")
                .body(new PgCancelRequest(command.pgTransactionId(), command.amount(), command.reason()))
                .retrieve()
                .body(PgCancelResponse.class));

        if (response == null) {
            throw new BusinessException(ErrorCode.PAYMENT_PG_UNAVAILABLE, "결제사 응답이 비어 있습니다.");
        }
        return new CancelResult(response.cancelId(), response.canceledAt());
    }

    private <T> T callWithRetry(String operation, java.util.function.Supplier<T> call) {
        ResourceAccessException lastFailure = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                lastFailure = e;
                log.warn("결제사 {} 통신 실패 ({}/{}): {}",
                        operation, attempt, properties.maxAttempts(), e.getMessage());
            }
        }
        throw new BusinessException(ErrorCode.PAYMENT_PG_UNAVAILABLE,
                "결제사 %s 요청이 %d회 모두 실패했습니다: %s"
                        .formatted(operation, properties.maxAttempts(),
                                lastFailure == null ? "원인 미상" : lastFailure.getMessage()));
    }

    record PgApproveRequest(String paymentKey, String orderKey, String method, BigDecimal amount) {
    }

    record PgApproveResponse(boolean approved, String transactionId, Instant approvedAt, String failureReason) {
    }

    record PgCancelRequest(String transactionId, BigDecimal amount, String reason) {
    }

    record PgCancelResponse(String cancelId, Instant canceledAt) {
    }
}
