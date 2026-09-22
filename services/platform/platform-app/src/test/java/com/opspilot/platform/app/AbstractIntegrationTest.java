package com.opspilot.platform.app;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
// Testcontainers 2.x moved the module containers into their own packages;
// org.testcontainers.containers.PostgreSQLContainer is deprecated.
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for integration tests, providing real PostgreSQL and Redis.
 *
 * <p>The containers are static and started once for the whole test run, then reused by every
 * subclass. Testcontainers' Ryuk sidecar removes them when the JVM exits. Starting a fresh
 * pair per test class would add roughly five seconds each, which is how integration suites end
 * up being skipped.
 *
 * <p>Image versions match deployment/docker/docker-compose.yml exactly. Testing against a
 * different version than you run is a way of discovering incompatibilities in production.
 * Dependabot cannot see these constants, so a Compose image bump must be applied here in the
 * same commit.
 *
 * <p>The application connects as {@code opspilot_app}, created NOBYPASSRLS by
 * db/testcontainers-init.sql, while Flyway connects as the migrator. That is the same split
 * used everywhere else, so tests exercise the real privilege model.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String MIGRATOR_USER = "opspilot_migrator";
    protected static final String MIGRATOR_PASSWORD = "test_migrator_pw";
    protected static final String APP_USER = "opspilot_app";
    protected static final String APP_PASSWORD = "test_app_pw";

    // Not generic in Testcontainers 2.x - the self-type parameter was dropped.
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
                    DockerImageName.parse("postgres:17.11-alpine"))
            .withDatabaseName("opspilot")
            .withUsername(MIGRATOR_USER)
            .withPassword(MIGRATOR_PASSWORD)
            .withInitScript("db/testcontainers-init.sql");

    protected static final GenericContainer<?> REDIS = new GenericContainer<>(
                    DockerImageName.parse("redis:8.8.2-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--requirepass", "test_redis_pw");

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        // Flyway runs as the schema owner...
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", () -> MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> MIGRATOR_PASSWORD);

        // ...the application does not.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_USER);
        registry.add("spring.datasource.password", () -> APP_PASSWORD);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "test_redis_pw");
    }
}
