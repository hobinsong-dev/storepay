package dev.hobin.storepay.common.idempotency;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등성 키 선점.
 *
 * <p>모든 공개 메서드가 {@code REQUIRES_NEW} 다. 호출 측(결제 승인) 트랜잭션에 묶이면 두 가지가 깨진다.
 * 첫째, 유니크 제약 위반이 바깥 트랜잭션까지 rollback-only 로 만들어
 * "이미 처리된 요청입니다" 응답조차 못 준다. 둘째, 선점 사실이 커밋되지 않아
 * 동시에 들어온 두 번째 요청이 선점을 보지 못하고 그대로 통과한다.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRecordRepository repository;

    /**
     * 키를 선점한다.
     *
     * <p>삽입을 먼저 시도하고 실패하면 조회하는 순서로 짜면 안 된다. 제약 위반이 난 세션은
     * 그 뒤 어떤 조회도 안전하지 않다(Hibernate 가 flush 시점에 AssertionFailure 를 던진다).
     * 그래서 <b>조회 먼저, 삽입은 마지막</b>으로 두고, 삽입이 실패하는 경우는
     * 조회와 삽입 사이에 끼어든 동시 요청뿐이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Reservation reserve(String idempotencyKey, String requestFingerprint) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
        }

        Optional<IdempotencyRecord> existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return evaluate(existing.get(), requestFingerprint);
        }

        try {
            repository.saveAndFlush(new IdempotencyRecord(idempotencyKey, requestFingerprint));
            return Reservation.fresh();
        } catch (DataIntegrityViolationException e) {
            // 선점 경쟁에서 졌다. 이긴 쪽이 아직 처리 중이므로 지금 진행하면 중복 승인이 된다.
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
    }

    private Reservation evaluate(IdempotencyRecord record, String requestFingerprint) {
        if (!record.getRequestFingerprint().equals(requestFingerprint)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        if (!record.isCompleted()) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_IN_PROGRESS);
        }
        return Reservation.replay(record.getResourceKey());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(String idempotencyKey, String resourceKey) {
        repository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(record -> record.complete(resourceKey));
    }

    /**
     * 처리에 실패했으면 선점을 푼다.
     * 풀지 않으면 그 키는 영원히 IDEMPOTENCY_IN_PROGRESS 를 돌려주고 손님은 재시도할 수 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(String idempotencyKey) {
        repository.findByIdempotencyKey(idempotencyKey)
                .filter(record -> !record.isCompleted())
                .ifPresent(repository::delete);
    }

    public static String fingerprint(String... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", e);
        }
    }

    /** 선점 결과. 처음이면 그대로 처리하고, 재요청이면 저장된 결과를 돌려준다. */
    public record Reservation(boolean replayed, String resourceKey) {

        static Reservation fresh() {
            return new Reservation(false, null);
        }

        static Reservation replay(String resourceKey) {
            return new Reservation(true, resourceKey);
        }
    }
}
