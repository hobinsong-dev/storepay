package dev.hobin.storepay.support;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * 실제 PostgreSQL 컨테이너를 띄워 Flyway 마이그레이션과 JPA 매핑까지 함께 검증한다.
 * H2 로 대체하면 방언 차이 때문에 마이그레이션 SQL 이 운영에서만 깨진다.
 *
 * <p>컨테이너는 <b>JVM 당 한 번만</b> 띄운다({@code @Container} 를 쓰지 않는 이유).
 * {@code @Container} 로 두면 테스트 클래스가 끝날 때마다 컨테이너가 내려가는데,
 * Spring 컨텍스트는 클래스 간에 캐시되어 살아남는다. 두 번째 통합 테스트 클래스는
 * 이미 죽은 컨테이너를 가리키는 DataSource 를 그대로 물고 커넥션 타임아웃으로 전부 실패한다.
 *
 * <p>Docker 가 없으면 컨테이너를 띄우지 않고 테스트 자체를 건너뛴다.
 */
@Tag("integration")
@SpringBootTest
@EnabledIf("dev.hobin.storepay.support.DockerAvailability#isAvailable")
public abstract class IntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("storepay")
                    .withUsername("storepay")
                    .withPassword("storepay");

    static {
        if (DockerAvailability.isAvailable()) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
