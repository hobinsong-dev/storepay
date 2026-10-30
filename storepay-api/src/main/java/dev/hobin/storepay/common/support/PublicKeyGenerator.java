package dev.hobin.storepay.common.support;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * 외부에 노출되는 식별자를 만든다.
 *
 * <p>PK(bigint)를 그대로 노출하면 가맹점 수나 주문량이 추측되고, 순차 증가라 스크래핑에도 취약하다.
 * 생성 시각(밀리초) 36진수 + 난수 조합이라 시간순 정렬이 보존되면서 값은 예측할 수 없다.
 */
public final class PublicKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyz";
    private static final int RANDOM_LENGTH = 10;

    private PublicKeyGenerator() {
    }

    public static String generate(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append('_');
        sb.append(Long.toString(Instant.now().toEpochMilli(), 36));
        sb.append('_');
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
