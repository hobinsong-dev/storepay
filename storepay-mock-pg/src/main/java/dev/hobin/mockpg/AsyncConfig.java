package dev.hobin.mockpg;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
@ConfigurationPropertiesScan
public class AsyncConfig {
}
