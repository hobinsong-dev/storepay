package dev.hobin.storepay.payment.infrastructure;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storepay.pg")
public record PgProperties(
        String baseUrl,
        String apiKey,
        String webhookSecret,
        Duration connectTimeout,
        Duration readTimeout,
        int maxAttempts,
        Duration webhookTolerance) {

    public PgProperties {
        connectTimeout = connectTimeout != null ? connectTimeout : Duration.ofSeconds(2);
        // 결제 승인은 읽기 타임아웃을 짧게 잡으면 안 된다. 끊어도 결제사 쪽에서는 승인이 끝나
        // '미결 거래'가 남는다. 끊는 것보다 기다리는 편이 복구 비용이 작다.
        readTimeout = readTimeout != null ? readTimeout : Duration.ofSeconds(10);
        maxAttempts = maxAttempts > 0 ? maxAttempts : 2;
        webhookTolerance = webhookTolerance != null ? webhookTolerance : Duration.ofMinutes(5);
    }
}
