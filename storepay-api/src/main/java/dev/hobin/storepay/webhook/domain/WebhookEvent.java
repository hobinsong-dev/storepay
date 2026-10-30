package dev.hobin.storepay.webhook.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수신한 웹훅 이벤트.
 *
 * <p>결제사는 응답을 못 받으면 같은 이벤트를 다시 보낸다(at-least-once).
 * 이벤트 ID 에 UNIQUE 를 걸어 두 번째부터는 조용히 무시한다.
 * 이 기록이 없으면 취소 웹훅이 두 번 들어와 환불이 두 번 일어날 수 있다.
 */
@Getter
@Entity
@Table(name = "webhook_event")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 100)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public WebhookEvent(String eventId, String eventType, String payload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.payload = payload;
        this.receivedAt = Instant.now();
    }
}
