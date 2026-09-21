# OpsPilot Platform

The modular monolith. One Spring Boot application built from nine Maven modules, with the
boundaries between them enforced at build time.

See [ADR-0001](../../docs/adr/0001-modular-monolith.md) for why it is structured this way,
and [the component diagram](../../docs/architecture/README.md#level-3--components-inside-the-platform)
for what each module owns.

---

## Modules

| Module | Owns |
|---|---|
| `platform-common` | Shared kernel — error model, `TenantContext`, domain event base types. Depends on no other module |
| `platform-identity` | Tenants, users, roles, permissions, credentials, tokens |
| `platform-servicedesk` | Tickets, comments, attachments, categories, teams |
| `platform-sla` | SLA policies, targets, business calendars, clocks, breach detection |
| `platform-workflow` | Approval chains, steps, decisions |
| `platform-asset` | Assets, assignments, lifecycle |
| `platform-knowledge` | Knowledge base articles, versions, tags |
| `platform-audit` | Append-only audit trail |
| `platform-app` | Composition root — the only executable jar, and the home of the ArchUnit boundary tests |

Dependencies flow one way only: domain modules depend on `platform-common`, and
`platform-app` depends on everything. Nothing depends on `platform-app`.

---

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 | Temurin recommended. The build refuses to start on anything older |
| Maven | — | Not required. Use the bundled wrapper: `./mvnw` |

---

## Common commands

The backing services must be running first:

```bash
cd ../../deployment/docker && docker compose up -d
```

```bash
# Full build with every quality gate - what CI runs
./mvnw clean verify

# Fix all formatting automatically
./mvnw spotless:apply

# Fast loop while developing - skips formatting, Checkstyle, coverage and ITs
./mvnw -Pfast clean install

# Unit tests only
./mvnw test

# One module and the modules it depends on
./mvnw -pl platform-identity -am test

# Run the application
./mvnw -pl platform-app spring-boot:run
```

On Windows Command Prompt or PowerShell, use `mvnw.cmd` instead of `./mvnw`.

Once running, the application is at <http://localhost:8080>:

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Overall status with per-component detail |
| `/actuator/health/liveness` | Kubernetes liveness probe — is the process alive? |
| `/actuator/health/readiness` | Kubernetes readiness probe — can it serve traffic? |
| `/actuator/prometheus` | Metrics, scraped in Phase 7 |
| `/actuator/info` | Build and runtime information |

---

## Database migrations

Flyway owns the schema. Hibernate runs with `ddl-auto: validate`, so it verifies that
the entities match what the migrations produced and refuses to start if they diverge.

Migrations live in
[`platform-app/src/main/resources/db/migration`](platform-app/src/main/resources/db/migration)
and are named `V<n>__<description>.sql` — two underscores, lower_snake_case description.

**Flyway connects as `opspilot_migrator`; the application connects as `opspilot_app`.**
The running application therefore has no privilege to alter the schema at all. Structural
change goes through a reviewed migration, or it does not happen:

```
$ psql -U opspilot_app -c "CREATE TABLE should_fail(id int);"
ERROR:  permission denied for schema public
```

Rules:

- **Never edit a migration that has run anywhere.** Flyway stores a checksum; changing an
  applied file fails validation on every environment that already has it. Write a new one
- Forward-only. No `undo` migrations — a mistake is corrected by a new migration
- Breaking changes use expand/contract, so a deploy never requires downtime
- `clean` is disabled, so `flyway:clean` cannot wipe a database by accident

---

## What the build enforces

Every gate below fails the build. None of them is advisory.

| Gate | Phase | Rejects |
|---|---|---|
| **Enforcer** | `validate` | Java below 21, Maven below 3.9, SNAPSHOT dependencies in a release, duplicate dependency declarations |
| **Spotless** | `validate` | Any formatting deviation — indentation, import order, trailing whitespace, unused imports. `spotless:apply` fixes all of it |
| **Checkstyle** | `validate` | Naming, missing braces, empty catch blocks, `equals` without `hashCode`, `System.out`, excessive complexity, missing package documentation |
| **Surefire** | `test` | Failing unit tests (`*Test`) |
| **Failsafe** | `integration-test` | Failing integration tests (`*IT`) |
| **JaCoCo** | `verify` | Line coverage below 75% per module |

The two test plugins are deliberately separate. `*Test` classes are fast and have no
Spring context or containers; `*IT` classes use Testcontainers and take seconds each. A
developer running `./mvnw test` should wait a moment, not a coffee break.

---

## Tests

| Test | Type | Asserts |
|---|---|---|
| `ModuleBoundaryTest` | ArchUnit, fast | The five boundary rules from ADR-0001 |
| `CodingRulesTest` | ArchUnit, fast | No `System.out`, no field injection, no generic exceptions |
| `PlatformSmokeIT` | Testcontainers | The app starts on real infrastructure and Flyway actually ran |
| `TenantIsolationIT` | Testcontainers | Row-Level Security genuinely isolates tenants |

### Architecture tests

`ModuleBoundaryTest` is the executable form of ADR-0001. Enforcing boundaries in code
review does not work — reviewers get tired, and one `@ManyToOne` across a boundary is easy
to miss and expensive to undo. A violation produces a failure naming the ADR:

```
Architecture Violation - Rule 'no classes that reside in a package
'com.opspilot.platform.*.domain..' should depend on classes that reside in
'org.springframework..', because domain logic must be testable without a
Spring context (ADR-0001)' was violated (1 times):
Class <com.opspilot.platform.sla.domain.SlaClock> is annotated with
<org.springframework.stereotype.Component>
```

Maven modules are the first line of defence — a module physically cannot compile against a
module it does not declare. ArchUnit is the second: it catches violations *within* the
dependencies a module legitimately has.

### Integration tests

Real PostgreSQL and Redis containers, at the exact versions in
[docker-compose.yml](../../deployment/docker/docker-compose.yml). **Never H2** — it has no
Row-Level Security, no `citext` and no JSONB, so passing against it would prove nothing.

Containers are static and started once per test run, then shared by every test class.
Per-class containers would add roughly five seconds each, which is how integration suites
end up being skipped.

`TenantIsolationIT` runs as the real `opspilot_app` role, created `NOBYPASSRLS` by
[testcontainers-init.sql](platform-app/src/test/resources/db/testcontainers-init.sql). That
detail is the whole test: the same assertions running as a superuser would pass while
proving nothing, because a superuser silently ignores RLS policies.

> **Requires Docker.** `./mvnw test` runs without it; `./mvnw verify` does not.

---

## Formatting

Formatting is not a matter of opinion here — **Palantir Java Format** decides, and Spotless
applies it. It is Google Java Format with a 4-space indent and a 120-column limit, which
matches [.editorconfig](../../.editorconfig).

If the build fails on formatting, do not fix it by hand:

```bash
./mvnw spotless:apply
```

Checkstyle deliberately contains **no** formatting rules. Two tools with opinions about
the same thing produce a build that can never be made green.

---

## Adding a module

1. Create the directory and a `pom.xml` whose parent is `com.opspilot:platform`
2. Add it to `<modules>` in the parent POM, in dependency order
3. Add it to `<dependencyManagement>` in the parent POM so its version is declared once
4. Add it as a dependency of `platform-app`
5. Create `src/main/java/com/opspilot/platform/<name>/package-info.java` documenting its
   boundary contract — Checkstyle requires it
6. Update the module table above and the component diagram

---

## Layout inside a module

```
platform-<name>/
└── src/main/java/com/opspilot/platform/<name>/
    ├── api/            # The ONLY package other modules may import
    ├── domain/         # Entities, value objects, state machines. No Spring, no JPA annotations
    ├── application/    # Use cases, transaction boundaries, event publication
    ├── infrastructure/ # JPA repositories, external clients, framework adapters
    └── web/            # Controllers, request and response DTOs
```

Dependencies point inward: `web` → `application` → `domain`. The `domain` package depends
on nothing, which is what makes it unit-testable without a Spring context.
