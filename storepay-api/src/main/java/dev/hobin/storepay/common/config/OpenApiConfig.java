package dev.hobin.storepay.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI storepayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("storepay API")
                .version("v0.1.0")
                .description("클라우드 POS 결제 백엔드 API"));
    }
}
