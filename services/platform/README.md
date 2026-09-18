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
