package dev.hobin.storepay.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시계를 빈으로 주입한다.
 *
 * <p>{@code Instant.now()} 를 코드 안에서 직접 부르면 "영업일 경계에서 어떻게 동작하는가"를
 * 테스트할 방법이 없다. 정산과 대사는 날짜 경계가 곧 사업 규칙이라 특히 그렇다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
