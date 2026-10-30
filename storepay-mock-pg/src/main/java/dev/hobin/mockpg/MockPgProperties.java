package dev.hobin.mockpg;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock-pg")
public record MockPgProperties(
        String apiKey,
        String webhookSecret,
        String webhookUrl,
        Duration approveLatency) {

    public MockPgProperties {
        approveLatency = approveLatency != null ? approveLatency : Duration.ZERO;
    }
}
