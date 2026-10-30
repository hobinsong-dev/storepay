package dev.hobin.mockpg;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 웹훅 발신.
 *
 * <p>별도 빈으로 뺀 이유는 {@code @Async} 가 프록시 기반이기 때문이다.
 * 같은 클래스 안에서 호출하면(self-invocation) 프록시를 거치지 않아 비동기가 적용되지 않고,
 * 승인 응답을 보내기 전에 웹훅이 먼저 날아간다.
 *
 * <p>2xx 를 받을 때까지 <b>물러나며 재전송한다</b>. 재전송이 없으면 승인 트랜잭션이 커밋되기 전에
 * 도착한 웹훅이 409 한 번으로 끝나고, 그 뒤로는 아무도 그 결제를 복구해 주지 않는다.
 * 수신 측이 "아직 모르는 결제"에 일부러 비(非)2xx 를 주는 설계는 발신 측이 다시 보낸다는 전제 위에서만 성립한다.
 * 이벤트 ID 는 시도마다 바꾸지 않는다 — 바꾸면 수신 측 중복 방지가 무력해져
 * 재전송이 곧 중복 처리가 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookSender {

    private final MockPgProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Async
    public void send(String eventType, String paymentKey, String transactionId,
                     BigDecimal amount, Instant occurredAt) {
        if (properties.webhookUrl() == null || properties.webhookUrl().isBlank()) {
            return;
        }
        String body;
        try {
            body = objectMapper.writeValueAsString(
                    new WebhookPayload(eventType, paymentKey, transactionId, amount, occurredAt));
        } catch (Exception e) {
            log.error("웹훅 본문을 만들지 못했습니다: paymentKey={}", paymentKey, e);
            return;
        }

        String eventId = UUID.randomUUID().toString();
        for (int attempt = 1; attempt <= properties.webhookMaxAttempts(); attempt++) {
            try {
                deliver(eventId, body);
                log.info("웹훅 전송 완료: eventType={}, paymentKey={}, attempt={}",
                        eventType, paymentKey, attempt);
                return;
            } catch (Exception e) {
                log.warn("웹훅 전송 실패 ({}/{}): paymentKey={}, reason={}",
                        attempt, properties.webhookMaxAttempts(), paymentKey, e.getMessage());
                if (attempt == properties.webhookMaxAttempts() || !backOff(attempt)) {
                    log.error("웹훅 전송을 포기합니다: paymentKey={}", paymentKey);
                    return;
                }
            }
        }
    }

    private void deliver(String eventId, String body) {
        // 서명은 시도마다 다시 만든다. 타임스탬프가 서명 대상에 들어 있어서,
        // 처음 만든 서명을 재사용하면 수신 측의 허용 시간 검사에 걸려 영영 통과하지 못한다.
        long timestamp = Instant.now().getEpochSecond();
        restClientBuilder.build().post()
                .uri(properties.webhookUrl())
                .header("Content-Type", "application/json")
                .header("X-Event-Id", eventId)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Signature", Signatures.sign(properties.webhookSecret(), timestamp, body))
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** 지수 백오프. 실패하자마자 같은 속도로 다시 때리면 수신 측이 회복할 틈이 없다. */
    private boolean backOff(int attempt) {
        try {
            Thread.sleep(properties.webhookRetryDelay().toMillis() * (1L << (attempt - 1)));
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    record WebhookPayload(String eventType, String paymentKey, String transactionId,
                          BigDecimal amount, Instant occurredAt) {
    }
}
