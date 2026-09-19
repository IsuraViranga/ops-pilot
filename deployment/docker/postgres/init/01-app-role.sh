#!/bin/bash
# ---------------------------------------------------------------------------
# Runs once, the first time the postgres volume is created.
#
# Creates the two-role setup that ADR-0002 depends on:
#
#   opspilot_migrator  - superuser, owns the schema, runs Flyway migrations.
#                        Created by the Postgres entrypoint from POSTGRES_USER.
#
#   opspilot_app       - what the application connects as. Deliberately NOT a
#                        superuser and explicitly NOBYPASSRLS, so Row-Level
#                        Security policies apply to it. A superuser silently
#                        ignores RLS, which would make our tenant isolation
#                        tests pass while proving nothing.
#
# To re-run this after changing it:  docker compose down -v && docker compose up -d
# ---------------------------------------------------------------------------
set -euo pipefail

psql -v ON_ERROR_STOP=1 \
     --username "$POSTGRES_USER" \
     --dbname "$POSTGRES_DB" \
     --set db_name="$POSTGRES_DB" \
     --set app_user="$APP_DB_USER" \
     --set app_password="$APP_DB_PASSWORD" <<-'SQL'

    -- ---------------------------------------------------------------
    -- Extensions
    -- ---------------------------------------------------------------
    -- pgcrypto : gen_random_bytes for tokens and salts
    -- citext   : case-insensitive text, used for email addresses so that
    --            Isura@x.com and isura@x.com cannot both register
    -- pg_trgm  : trigram indexes for fuzzy ticket and article search
    CREATE EXTENSION IF NOT EXISTS pgcrypto;
    CREATE EXTENSION IF NOT EXISTS citext;
    CREATE EXTENSION IF NOT EXISTS pg_trgm;

    -- ---------------------------------------------------------------
    -- Application role
    -- ---------------------------------------------------------------
    CREATE ROLE :"app_user" WITH
        LOGIN
        PASSWORD :'app_password'
        NOSUPERUSER
        NOCREATEDB
        NOCREATEROLE
        NOBYPASSRLS
        CONNECTION LIMIT 50;

    COMMENT ON ROLE :"app_user" IS
        'Least-privilege application role. NOBYPASSRLS is what makes tenant isolation real.';

    -- ---------------------------------------------------------------
    -- Privileges
    -- ---------------------------------------------------------------
    -- The app may use the schema but never create objects in it. Schema changes
    -- go through a reviewed Flyway migration or they do not happen.
    REVOKE CREATE ON SCHEMA public FROM PUBLIC;

    GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
    GRANT USAGE   ON SCHEMA public      TO :"app_user";

    -- Tables and sequences do not exist yet; the migrations create them. These
    -- default privileges grant access to everything the migrator creates later,
    -- so no migration ever has to remember to add a GRANT.
    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"app_user";

    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT USAGE, SELECT ON SEQUENCES TO :"app_user";

    ALTER DEFAULT PRIVILEGES IN SCHEMA public
        GRANT EXECUTE ON FUNCTIONS TO :"app_user";

SQL

echo "[init] role '$APP_DB_USER' created (NOSUPERUSER, NOBYPASSRLS)"
echo "[init] extensions installed: pgcrypto, citext, pg_trgm"
