# Local Development Environment

Four containers that stand in for the AWS services OpsPilot uses in production.

| Service | What it is | Stands in for | Host port |
|---|---|---|---|
| **postgres** | PostgreSQL 17.11 | Amazon RDS | `55432` |
| **redis** | Redis 8.8.2 | Amazon ElastiCache | `56379` |
| **mailpit** | Fake SMTP server + web inbox | Amazon SES | `51025` SMTP, `58025` web |
| **adminer** | Database browser | — | `58080` |

> **Why the odd port numbers?** `5432`, `6379` and `1025` are usually already in use by a
> locally installed Postgres, Redis or mail catcher. Rather than asking you to stop those,
> the defaults sit out of the way. Override any of them in `.env`.

---

## First run

```bash
cd deployment/docker
cp .env.example .env
docker compose up -d
```

Then check everything came up healthy:

```bash
docker compose ps
```

```
SERVICE    STATUS                   PORTS
adminer    Up 1 second              0.0.0.0:58080->8080/tcp
mailpit    Up 7 seconds (healthy)   0.0.0.0:51025->1025/tcp, 0.0.0.0:58025->8025/tcp
postgres   Up 7 seconds (healthy)   0.0.0.0:55432->5432/tcp
redis      Up 7 seconds (healthy)   0.0.0.0:56379->6379/tcp
```

---

## Where to click

| | |
|---|---|
| **Mail inbox** | <http://localhost:58025> — every email the app sends lands here |
| **Database browser** | <http://localhost:58080> — server `postgres`, user `opspilot_migrator`, database `opspilot` |

---

## Daily commands

```bash
docker compose up -d          # start
docker compose ps             # health check
docker compose logs -f        # follow all logs
docker compose logs -f postgres
docker compose stop           # pause, keep containers
docker compose down           # remove containers, KEEP data
docker compose down -v        # remove containers and DELETE ALL DATA
docker compose restart redis  # restart one service
```

Connect to the database directly:

```bash
docker compose exec postgres psql -U opspilot_migrator -d opspilot
```

Connect to Redis:

```bash
docker compose exec redis redis-cli -a local_redis_pw
```

---

## The two database roles

This is the part that matters, and it implements
[ADR-0002](../../docs/adr/0002-multi-tenancy-strategy.md).

| Role | Privileges | Used by |
|---|---|---|
| `opspilot_migrator` | Superuser, owns the schema | Flyway migrations |
| `opspilot_app` | `NOSUPERUSER`, **`NOBYPASSRLS`**, no `CREATE` on the schema | The application at runtime |

`NOBYPASSRLS` is the whole point. **A PostgreSQL superuser silently ignores Row-Level
Security policies.** If the application connected as a superuser, every tenant isolation
test would pass while proving absolutely nothing — the policies would be inert.

Separating the roles also means the running application physically cannot alter the
schema. Structural change goes through a reviewed Flyway migration, or it does not happen.

### Verifying it works

Given a table with a tenant policy:

```sql
SET app.current_tenant = 'tenant-a';
SELECT * FROM some_table;   -- only tenant-a rows
```

```sql
-- no tenant context set
SELECT count(*) FROM some_table;   -- 0
```

Returning **zero rows rather than all rows** when the context is missing is the correct
failure mode: a bug that forgets to set the tenant shows up immediately as an empty
screen, instead of quietly leaking another customer's data.

---

## What the init script does

[`postgres/init/01-app-role.sh`](postgres/init/01-app-role.sh) runs **once**, when the
data volume is first created:

1. Installs extensions — `pgcrypto` (token generation), `citext` (case-insensitive email,
   so `Isura@x.com` and `isura@x.com` cannot both register), `pg_trgm` (fuzzy search)
2. Creates the `opspilot_app` role with the restrictions above
3. Revokes `CREATE` on the `public` schema from `PUBLIC`
4. Sets **default privileges**, so every table a future migration creates is automatically
   readable and writable by the app role — no migration has to remember a `GRANT`

Because it only runs on first creation, changing it requires a wipe:

```bash
docker compose down -v && docker compose up -d
```

---

## Connection settings for the application

```properties
spring.datasource.url=jdbc:postgresql://localhost:55432/opspilot
spring.datasource.username=opspilot_app
spring.datasource.password=local_app_pw

spring.data.redis.host=localhost
spring.data.redis.port=56379
spring.data.redis.password=local_redis_pw

spring.mail.host=localhost
spring.mail.port=51025
```

Wired up properly in Part 5.

---

## Deliberate choices

| Choice | Reason |
|---|---|
| Exact image tags, not `latest` | `latest` means the environment changes underneath you without warning |
| A Redis password locally | If the app works without one locally and needs one in production, that gap is found during deployment. Better to find it now |
| `log_statement=all`, `log_min_duration_statement=200` | Every query is visible and anything over 200ms is flagged, so N+1 queries are caught while the code is being written |
| `--locale=C` | Deterministic sort order, so `ORDER BY` behaves identically on every machine and in CI |
| Health checks on every service | `depends_on: condition: service_healthy` means Adminer waits for a database that is genuinely ready, not merely started |
| Named volumes | Data survives `docker compose down`. Only `-v` deletes it |

---

## Troubleshooting

**Port already in use** — change the port in `.env` and run `docker compose up -d` again.

**Postgres unhealthy on first start** — check `docker compose logs postgres`. A syntax error
in the init script leaves the container running with no roles created. Fix it, then
`docker compose down -v && docker compose up -d`.

**Changed `.env` but nothing happened** — Compose reads `.env` at container creation.
Run `docker compose up -d --force-recreate`.

**Reset everything** — `docker compose down -v` deletes all local data and starts clean.
