package dev.hobin.storepay.common.idempotency;

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
 * 멱등성 기록.
 *
 * <p>Redis 가 아니라 DB 에 둔다. 결제 승인의 중복 방지는 결제 레코드 생성과 같은 트랜잭션 경계에서
 * 다뤄져야 하는데, 캐시에 두면 캐시만 날아갔을 때 중복 승인이 그대로 뚫린다.
 * 응답 속도보다 중복 결제를 막는 쪽이 중요하다.
 */
@Getter
@Entity
@Table(name = "idempotency_record")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    /** 요청 본문의 지문. 같은 키로 다른 내용이 오면 클라이언트 버그이므로 거부한다. */
    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    /** 처리 결과로 만들어진 리소스 키. 완료 전에는 null 이다. */
    @Column(name = "resource_key", length = 40)
    private String resourceKey;

    @Column(name = "completed_at")
    private Instant completedAt;

    IdempotencyRecord(String idempotencyKey, String requestFingerprint) {
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
    }

    void complete(String resourceKey) {
        this.resourceKey = resourceKey;
        this.completedAt = Instant.now();
    }

    public boolean isCompleted() {
        return this.completedAt != null;
    }
}
