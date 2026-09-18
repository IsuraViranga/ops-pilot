# Contributing to OpsPilot

These are the working rules for the repository. They exist so the project stays
reviewable, releasable and honest about its own quality — the same way a real
engineering team would run it.

---

## 1. Branching model

**Trunk-based development.** `main` is protected and always releasable.

```
main ──────●────────●────────●────────►
            \      /  \     /
             ●────●    ●───●
          feat/login   fix/sla-clock
```

| Rule | Detail |
|---|---|
| Branch from | `main`, always up to date |
| Lifetime | Under 2 days. Long branches mean the change is too big |
| Merge | Squash merge, so `main` keeps a linear, readable history |
| Delete | Branch is deleted after merge |

**Branch naming**

```
feat/<short-description>      new functionality
fix/<short-description>       bug fix
refactor/<short-description>  behaviour-preserving change
docs/<short-description>      documentation only
chore/<short-description>     tooling, dependencies, config
ci/<short-description>        pipeline changes
```

Example: `feat/refresh-token-rotation`

---

## 2. Commit messages — Conventional Commits

```
<type>(<scope>): <subject>

<body — why, not what>

<footer — breaking changes, issue references>
```

**Types**

| Type | Use for |
|---|---|
| `feat` | A new capability |
| `fix` | A bug fix |
| `refactor` | Restructuring with no behaviour change |
| `perf` | Performance improvement |
| `test` | Adding or correcting tests |
| `docs` | Documentation only |
| `build` | Build system, dependencies |
| `ci` | Pipeline configuration |
| `chore` | Housekeeping that fits nothing above |

**Scopes** — the module or area touched: `identity`, `servicedesk`, `sla`, `workflow`,
`asset`, `knowledge`, `audit`, `frontend`, `ai`, `infra`, `ci`, `docs`.

**Rules**

- Subject in the imperative mood: "add", not "added" or "adds"
- Subject under 72 characters, no full stop
- Body explains **why** the change was made; the diff already shows what
- Breaking changes get a `BREAKING CHANGE:` footer

**Examples**

```
feat(identity): add refresh token rotation with reuse detection

Refresh tokens are now single-use. Reusing a consumed token invalidates the
entire token family, which contains the blast radius of a stolen token.

Closes #42
```

```
fix(sla): stop the SLA clock while a ticket waits on the user

Time spent in WAITING_FOR_USER was counting against the resolution target,
which produced false breaches on tickets blocked by the reporter.
```

This format is not decoration — it drives the generated changelog and semantic
version bumps.

---

## 3. Pull requests

Every change reaches `main` through a pull request. No direct pushes.

**Before opening**

- [ ] Rebased on the latest `main`
- [ ] Full test suite green locally
- [ ] Formatter and linter clean
- [ ] Self-reviewed the diff

**Requirements to merge**

- [ ] CI green — build, tests, code quality gate, security scans
- [ ] At least one approving review
- [ ] All review conversations resolved
- [ ] No merge commits — rebase if `main` moved

Keep pull requests small. A 200-line PR gets a real review; a 2,000-line PR gets
a rubber stamp.

---

## 4. Definition of Done

A task is not done until **every** box is ticked:

- [ ] Code written and reviewed
- [ ] Unit tests added, suite green
- [ ] Integration tests added where the change crosses a boundary (Testcontainers)
- [ ] Flyway migration written, if the schema changed
- [ ] OpenAPI specification updated, if the API changed
- [ ] ADR written, if a significant decision was made
- [ ] Structured logging and metrics added for the new path
- [ ] CI pipeline green, all security gates passed
- [ ] Runs correctly from a clean `docker compose up`
- [ ] README or runbook updated, if operational behaviour changed

---

## 5. Code style

Formatting is automated and enforced in CI. Do not argue with the formatter.

| Language | Formatter | Linter | Static analysis |
|---|---|---|---|
| Java | Spotless + Google Java Format | Checkstyle | SpotBugs, ArchUnit, SonarQube |
| TypeScript | Prettier | ESLint (strict) | `tsc --noEmit`, SonarQube |
| Python | Black | Ruff | mypy, SonarQube |

Run before committing:

```bash
# Java
./mvnw spotless:apply

# Frontend
npm run format && npm run lint

# AI service
black . && ruff check --fix .
```

**Quality gate:** coverage at least 75% on new code, zero new blocker or critical
issues, no new security hotspots.

---

## 6. Architecture rules

These are enforced by ArchUnit tests — a violation fails the build, it is not a
matter of taste.

1. **Modules do not reach into each other's internals.** A module exposes an `api`
   package; everything else is internal.
2. **No cross-module JPA relationships.** Reference another module's entity by ID,
   never by `@ManyToOne`.
3. **No cross-module foreign keys** in the database.
4. **Modules communicate through published interfaces or domain events**, never by
   direct access to another module's repository.
5. **The domain layer depends on nothing.** No Spring annotations, no JPA, no HTTP in
   pure domain classes.

These rules are what make the Phase 4 microservice extraction a mechanical change
instead of a rewrite. They are non-negotiable.

---

## 7. Architecture Decision Records

Write an ADR whenever a decision is expensive to reverse, affects more than one
module, or a future reader would reasonably ask "why on earth did they do that?"

```
docs/adr/NNNN-short-title.md
```

Use the MADR structure: **Status · Context · Decision · Consequences · Alternatives
considered**. Copy [docs/adr/0000-template.md](docs/adr/0000-template.md).

ADRs are immutable. To change a decision, write a new ADR that supersedes the old
one and mark the old one `Superseded by ADR-NNNN`.

---

## 8. Security rules

- **Never commit secrets.** Gitleaks runs in CI, but the first defence is you.
- Local configuration goes in `.env`, which is gitignored. Commit `.env.example`
  with placeholder values instead.
- `tenant_id` is resolved **only** from the verified JWT — never from a path
  variable, query parameter, header or request body.
- Cross-tenant access returns **404, not 403**. A 403 confirms the resource exists.
- All user input is validated at the API boundary with Bean Validation or Zod.
- Dependencies with High or Critical vulnerabilities block the merge.

---

## 9. Getting help

- The roadmap and phase exit criteria: [PLAN.md](PLAN.md)
- Why something is built the way it is: [docs/adr/](docs/adr/)
- How the pieces fit together: [docs/architecture/](docs/architecture/)
