package dev.hobin.storepay.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 실제 PostgreSQL 컨테이너를 띄워 Flyway 마이그레이션과 JPA 매핑까지 함께 검증한다.
 *
 * <p>H2로 대체하면 방언 차이 때문에 마이그레이션 SQL이 운영에서만 깨진다.
 * Docker가 없는 환경에서는 실패가 아니라 건너뛴다(disabledWithoutDocker).
 */
@Tag("integration")
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
public abstract class IntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("storepay")
                    .withUsername("storepay")
                    .withPassword("storepay");
}
