-- ===========================================================================
-- V1 - Baseline
--
-- Establishes the foundations every later migration builds on. It creates no
-- business tables; those arrive with the modules that own them in Phase 1.
--
-- Runs as opspilot_migrator (the schema owner), never as the application role.
-- Configured in application.yml under spring.flyway.
-- ===========================================================================


-- ---------------------------------------------------------------------------
-- Extensions
--
-- The local Docker environment installs these too, but a migration must be able
-- to bring an empty database (a fresh RDS instance, or a Testcontainers
-- instance in CI) to a working state entirely on its own.
-- ---------------------------------------------------------------------------

-- Cryptographic primitives: gen_random_bytes for tokens, digest for hashing.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Case-insensitive text. Email addresses use this, so Isura@x.com and
-- isura@x.com cannot both be registered as separate accounts.
CREATE EXTENSION IF NOT EXISTS citext;

-- Trigram indexes, for fuzzy search over ticket titles and article text.
CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- ---------------------------------------------------------------------------
-- Tenant context
--
-- The single source of truth for "which tenant is this connection acting as".
-- Every Row-Level Security policy in the system calls this function, so the
-- rule lives in exactly one place.
--
-- The application sets the value per transaction:
--
--     SET LOCAL app.current_tenant = '<uuid>';
--
-- SET LOCAL, not SET: the value is discarded when the transaction ends, so a
-- pooled connection can never carry one request's tenant into the next.
--
-- The second argument to current_setting() is missing_ok. Without it, a query
-- on a connection that has not set the variable raises an error instead of
-- returning NULL - and NULL is what we want, because a policy comparing
-- against NULL matches no rows.
--
-- Returning NO ROWS when the context is missing is the correct failure mode.
-- Code that forgets to set the tenant shows an empty screen, which is obvious
-- and harmless. The alternative - returning everything - is a data breach.
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION current_tenant_id()
    RETURNS uuid
    LANGUAGE sql
    STABLE
    PARALLEL SAFE
AS $$
    SELECT NULLIF(current_setting('app.current_tenant', true), '')::uuid
$$;

COMMENT ON FUNCTION current_tenant_id() IS
    'Tenant of the current transaction, from the app.current_tenant setting. '
    'NULL when unset, which makes every RLS policy match zero rows. See ADR-0002.';


-- ---------------------------------------------------------------------------
-- Automatic updated_at
--
-- Maintaining this in application code means every write path has to remember.
-- A trigger cannot forget, and it is also correct for writes that bypass the
-- application, such as a manual fix applied during an incident.
--
-- Attach it to a table with:
--
--     CREATE TRIGGER set_updated_at
--         BEFORE UPDATE ON <table>
--         FOR EACH ROW
--         EXECUTE FUNCTION set_updated_at();
-- ---------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION set_updated_at() IS
    'BEFORE UPDATE trigger function that stamps updated_at with the current time.';


-- ---------------------------------------------------------------------------
-- Privileges
--
-- The default privileges configured when the database was created cover tables
-- and sequences created from this point on. Functions created in this migration
-- are granted explicitly, because they exist before any later default applies.
-- ---------------------------------------------------------------------------

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'opspilot_app') THEN
        GRANT EXECUTE ON FUNCTION current_tenant_id() TO opspilot_app;
        GRANT EXECUTE ON FUNCTION set_updated_at() TO opspilot_app;
    END IF;
END
$$;
