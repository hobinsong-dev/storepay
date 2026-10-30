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
        try {
            String body = objectMapper.writeValueAsString(
                    new WebhookPayload(eventType, paymentKey, transactionId, amount, occurredAt));
            long timestamp = Instant.now().getEpochSecond();

            restClientBuilder.build().post()
                    .uri(properties.webhookUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Event-Id", UUID.randomUUID().toString())
                    .header("X-Timestamp", String.valueOf(timestamp))
                    .header("X-Signature", Signatures.sign(properties.webhookSecret(), timestamp, body))
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();

            log.info("웹훅 전송 완료: eventType={}, paymentKey={}", eventType, paymentKey);
        } catch (Exception e) {
            // 실제 결제사도 수신 측이 2xx 를 주지 않으면 재전송한다. 여기서는 로그만 남긴다.
            log.warn("웹훅 전송 실패 (수신 측이 거절했거나 아직 준비되지 않음): paymentKey={}, reason={}",
                    paymentKey, e.getMessage());
        }
    }

    record WebhookPayload(String eventType, String paymentKey, String transactionId,
                          BigDecimal amount, Instant occurredAt) {
    }
}
