-- ===========================================================================
-- Container bootstrap for integration tests.
--
-- Mirrors deployment/docker/postgres/init/01-app-role.sh: it creates the same
-- least-privileged application role, so tests exercise the same privilege
-- model as local development and production.
--
-- Runs once per container, as the container superuser (opspilot_migrator),
-- before Flyway.
-- ===========================================================================

CREATE ROLE opspilot_app WITH
    LOGIN
    PASSWORD 'test_app_pw'
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    -- The reason this file exists. A superuser silently ignores Row-Level
    -- Security, so an isolation test running as one would pass while proving
    -- nothing at all.
    NOBYPASSRLS;

REVOKE CREATE ON SCHEMA public FROM PUBLIC;

GRANT CONNECT ON DATABASE opspilot TO opspilot_app;
GRANT USAGE ON SCHEMA public TO opspilot_app;

-- Applies to everything Flyway creates afterwards.
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO opspilot_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO opspilot_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT EXECUTE ON FUNCTIONS TO opspilot_app;
