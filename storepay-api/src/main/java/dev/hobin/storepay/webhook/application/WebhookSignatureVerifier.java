package dev.hobin.storepay.webhook.application;

import dev.hobin.storepay.common.exception.BusinessException;
import dev.hobin.storepay.common.exception.ErrorCode;
import dev.hobin.storepay.payment.infrastructure.PgProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 웹훅 서명 검증.
 *
 * <p>웹훅 엔드포인트는 인증 없이 외부에 열려 있다. 서명을 검증하지 않으면 누구나
 * "결제가 승인됐다"는 요청을 보내 상품을 가져갈 수 있다.
 */
@Component
@RequiredArgsConstructor
public class WebhookSignatureVerifier {

    private static final String ALGORITHM = "HmacSHA256";

    private final PgProperties properties;

    public void verify(String signature, String timestampHeader, String rawBody) {
        long timestamp = parseTimestamp(timestampHeader);

        // 서명이 유효해도 오래된 요청은 버린다. 가로챈 요청을 그대로 재전송하는 공격을 막는다.
        long skewSeconds = Math.abs(Instant.now().getEpochSecond() - timestamp);
        if (skewSeconds > properties.webhookTolerance().toSeconds()) {
            throw new BusinessException(ErrorCode.WEBHOOK_STALE_TIMESTAMP,
                    "타임스탬프 차이가 %d초입니다.".formatted(skewSeconds));
        }

        String expected = sign(timestamp, rawBody);
        if (signature == null || !constantTimeEquals(expected, signature)) {
            throw new BusinessException(ErrorCode.WEBHOOK_INVALID_SIGNATURE);
        }
    }

    private long parseTimestamp(String timestampHeader) {
        try {
            return Long.parseLong(timestampHeader);
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(ErrorCode.WEBHOOK_STALE_TIMESTAMP, "타임스탬프 헤더가 없거나 형식이 잘못됐습니다.");
        }
    }

    private String sign(long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.webhookSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("웹훅 서명을 계산할 수 없습니다.", e);
        }
    }

    /**
     * 길이와 내용 비교에 걸리는 시간이 입력에 따라 달라지지 않게 한다.
     * 일반 equals 는 앞에서부터 다른 바이트가 나오면 바로 반환해, 응답 시간 차이로
     * 올바른 서명을 한 바이트씩 알아낼 수 있다.
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }
}
