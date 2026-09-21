package com.opspilot.platform.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end check that the application starts against real infrastructure and that the
 * baseline migration was genuinely applied.
 *
 * <p>The Flyway assertions exist for a specific reason. During Part 5 the application started
 * cleanly and /actuator/health reported UP while Flyway had never run at all, because Spring
 * Boot 4 requires the spring-boot-flyway auto-configuration module in addition to flyway-core.
 * A green health check did not catch an empty database. These assertions do.
 */
class PlatformSmokeIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Spring Boot 4 removed TestRestTemplate. The JDK client is sufficient here and keeps the
    // test independent of whichever HTTP abstraction the framework favours next.
    @LocalServerPort
    private int port;

    @Test
    @DisplayName("readiness reports UP with a real database and Redis behind it")
    void readinessIsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health/readiness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("liveness ignores the database, so a database blip cannot trigger a pod restart")
    void livenessDoesNotDependOnTheDatabase() throws Exception {
        HttpResponse<String> response = get("/actuator/health/liveness");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("\"db\"").doesNotContain("\"redis\"");
    }

    @Test
    @DisplayName("Flyway applied the baseline migration")
    void baselineMigrationWasApplied() {
        List<String> applied = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank", String.class);

        assertThat(applied).as("migrations recorded in flyway_schema_history").contains("1");
    }

    @Test
    @DisplayName("the baseline created the functions later migrations depend on")
    void baselineFunctionsExist() {
        List<String> functions = jdbcTemplate.queryForList("""
                SELECT p.proname
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = 'public'
                """, String.class);

        assertThat(functions).contains("current_tenant_id", "set_updated_at");
    }

    @Test
    @DisplayName("the required extensions are installed")
    void extensionsAreInstalled() {
        List<String> extensions = jdbcTemplate.queryForList("SELECT extname FROM pg_extension", String.class);

        assertThat(extensions).contains("pgcrypto", "citext", "pg_trgm");
    }

    @Test
    @DisplayName("current_tenant_id() is null when no tenant context has been set")
    void tenantIdIsNullWithoutContext() {
        // The safe default. A policy comparing against NULL matches no rows, so code that
        // forgets to set the tenant shows an empty screen rather than another tenant's data.
        UUID tenantId = jdbcTemplate.queryForObject("SELECT current_tenant_id()", UUID.class);

        assertThat(tenantId).isNull();
    }

    @Test
    @Transactional
    @DisplayName("current_tenant_id() returns the tenant set for the transaction")
    void tenantIdIsReadBackWithinTheTransaction() {
        UUID expected = UUID.fromString("11111111-1111-1111-1111-111111111111");

        // SET LOCAL, not SET: the value is discarded when the transaction ends, so a pooled
        // connection cannot carry one request's tenant into the next request.
        jdbcTemplate.execute("SET LOCAL app.current_tenant = '" + expected + "'");

        UUID actual = jdbcTemplate.queryForObject("SELECT current_tenant_id()", UUID.class);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("the application role cannot alter the schema")
    void applicationRoleCannotChangeTheSchema() {
        // Structural change goes through a reviewed Flyway migration, or it does not happen.
        assertThat(jdbcTemplate.queryForObject("SELECT current_user", String.class))
                .isEqualTo(APP_USER);

        // Spring wraps the driver exception, so the PostgreSQL message is on the root cause.
        assertThatThrownBy(() -> jdbcTemplate.execute("CREATE TABLE should_not_exist (id int)"))
                .rootCause()
                .hasMessageContaining("permission denied for schema public");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .GET()
                .build();

        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }
}
