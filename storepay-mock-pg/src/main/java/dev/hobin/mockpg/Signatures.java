package dev.hobin.mockpg;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 웹훅 서명.
 *
 * <p>본문만 서명하면 공격자가 가로챈 요청을 그대로 다시 보낼 수 있다(replay).
 * 타임스탬프를 서명 대상에 포함시켜, 수신 측이 허용 시간을 벗어난 요청을 버릴 수 있게 한다.
 */
public final class Signatures {

    private static final String ALGORITHM = "HmacSHA256";

    private Signatures() {
    }

    public static String sign(String secret, long timestamp, String body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            byte[] hash = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("웹훅 서명을 만들 수 없습니다.", e);
        }
    }
}
