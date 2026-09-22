package com.opspilot.platform.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves that Row-Level Security genuinely isolates tenants.
 *
 * <p>This is the security property the whole platform rests on, so it is asserted rather than
 * assumed. The test runs against the real {@code opspilot_app} role, which is created
 * NOBYPASSRLS. That detail is the entire point: a PostgreSQL superuser silently ignores RLS
 * policies, so the same test running as a superuser would pass while proving nothing.
 *
 * <p>Phase 1 replaces this probe table with the real tenant-scoped tables. The mechanism being
 * verified stays identical.
 *
 * @see <a href="../../../../../../../../../docs/adr/0002-multi-tenancy-strategy.md">ADR-0002</a>
 */
class TenantIsolationIT extends AbstractIntegrationTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    /**
     * Guards one-time setup. The probe table lives in the shared container, so it only needs
     * creating once no matter how many test methods run.
     */
    private static boolean probeTableCreated;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Creates the probe table as the migrator, because the application role deliberately has no
     * privilege to create tables. This mirrors how Flyway will create the real tables.
     *
     * <p>Deliberately {@code @BeforeEach} rather than {@code @BeforeAll}. The policy below calls
     * {@code current_tenant_id()}, which the Flyway baseline creates - and Flyway runs when the
     * Spring context starts, which JUnit does <em>after</em> {@code @BeforeAll} but <em>before</em>
     * {@code @BeforeEach}. As {@code @BeforeAll} this passed locally purely because
     * PlatformSmokeIT happened to run first and booted the context; on Linux the test classes were
     * ordered differently and it failed with "function current_tenant_id() does not exist".
     */
    @BeforeEach
    void createProbeTable() throws SQLException {
        if (probeTableCreated) {
            return;
        }

        runAsMigrator("""
                CREATE TABLE tenant_probe (
                    id        int PRIMARY KEY,
                    tenant_id uuid NOT NULL,
                    secret    text NOT NULL
                );

                INSERT INTO tenant_probe VALUES
                    (1, 'aaaaaaaa-0000-0000-0000-000000000001', 'belongs to tenant A'),
                    (2, 'bbbbbbbb-0000-0000-0000-000000000002', 'belongs to tenant B');

                ALTER TABLE tenant_probe ENABLE ROW LEVEL SECURITY;
                -- FORCE also applies the policy to the table owner, so a future migration
                -- that queries this table is subject to it too.
                ALTER TABLE tenant_probe FORCE ROW LEVEL SECURITY;

                CREATE POLICY tenant_isolation ON tenant_probe
                    USING (tenant_id = current_tenant_id());

                GRANT SELECT ON tenant_probe TO opspilot_app;
                """);

        markProbeTableCreated();
    }

    private static void markProbeTableCreated() {
        probeTableCreated = true;
    }

    @AfterAll
    static void dropProbeTable() throws SQLException {
        // Safe as @AfterAll: by now the context has started and the table exists.
        runAsMigrator("DROP TABLE IF EXISTS tenant_probe;");
        probeTableCreated = false;
    }

    @Test
    @Transactional
    @DisplayName("a tenant sees only its own rows")
    void tenantSeesOnlyItsOwnRows() {
        setTenant(TENANT_A);

        assertThat(jdbcTemplate.queryForList("SELECT secret FROM tenant_probe", String.class))
                .containsExactly("belongs to tenant A");
    }

    @Test
    @Transactional
    @DisplayName("a tenant cannot reach another tenant's row even by asking for it directly")
    void tenantCannotReachAnotherTenantsRowById() {
        setTenant(TENANT_A);

        // Requesting tenant B's row by primary key returns nothing. At the API layer this
        // becomes a 404, not a 403 - a 403 would confirm that the row exists.
        assertThat(jdbcTemplate.queryForList("SELECT secret FROM tenant_probe WHERE id = 2", String.class))
                .isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("the other tenant sees its own row, so the policy filters rather than blocks")
    void theOtherTenantSeesItsOwnRow() {
        setTenant(TENANT_B);

        assertThat(jdbcTemplate.queryForList("SELECT secret FROM tenant_probe", String.class))
                .containsExactly("belongs to tenant B");
    }

    @Test
    @DisplayName("with no tenant context set, nothing is visible at all")
    void noTenantContextMeansNoRows() {
        // The failure mode that matters. Code that forgets to set the tenant produces an
        // empty result, which is obvious and harmless. Returning every row would be a breach.
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM tenant_probe", Integer.class))
                .isZero();
    }

    @Test
    @DisplayName("the application role cannot bypass Row-Level Security")
    void applicationRoleCannotBypassRls() {
        // If this ever reports true, every isolation test above becomes meaningless.
        Boolean bypassesRls = jdbcTemplate.queryForObject(
                "SELECT rolbypassrls FROM pg_roles WHERE rolname = current_user", Boolean.class);

        assertThat(bypassesRls)
                .as("%s must not be able to bypass RLS", APP_USER)
                .isFalse();
    }

    private void setTenant(UUID tenantId) {
        // SET LOCAL scopes the value to this transaction only, so a pooled connection cannot
        // carry it into the next request.
        jdbcTemplate.execute("SET LOCAL app.current_tenant = '" + tenantId + "'");
    }

    private static void runAsMigrator(String sql) throws SQLException {
        try (Connection connection =
                        java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), MIGRATOR_USER, MIGRATOR_PASSWORD);
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
