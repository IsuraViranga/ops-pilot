# OpsPilot

[![Backend](https://github.com/IsuraViranga/ops-pilot/actions/workflows/backend.yml/badge.svg)](https://github.com/IsuraViranga/ops-pilot/actions/workflows/backend.yml)
[![Frontend](https://github.com/IsuraViranga/ops-pilot/actions/workflows/frontend.yml/badge.svg)](https://github.com/IsuraViranga/ops-pilot/actions/workflows/frontend.yml)
[![Security](https://github.com/IsuraViranga/ops-pilot/actions/workflows/security.yml/badge.svg)](https://github.com/IsuraViranga/ops-pilot/actions/workflows/security.yml)

**AI-Powered Enterprise Service Management Platform**

A multi-tenant IT service management platform — tickets, SLAs, approval workflows, asset
tracking and a knowledge base — with an AI assistant that answers from company knowledge
rather than guessing.

> **Status: Phase 0 — Foundation.** The repository skeleton is in place. No business
> functionality yet. See [PLAN.md](PLAN.md) for the full roadmap.

---

## What problem it solves

An employee reports *"My laptop cannot connect to the company VPN."* The platform then:

1. Identifies the employee and their tenant
2. Classifies the issue (AI suggests, an agent confirms)
3. Assigns a priority and routes it to the right support team
4. Starts an SLA clock and escalates before it breaches
5. Tracks every status change in an immutable audit trail
6. Captures the resolution back into the knowledge base
7. Lets the next employee with the same problem get a grounded, cited answer instantly

---

## Architecture at a glance

Built as a **modular monolith with build-time-enforced boundaries**, designed for
extraction into microservices in Phase 4. See
[ADR-0001](docs/adr/0001-modular-monolith.md) for why.

```
Next.js Portal  ──►  API Gateway  ──►  Platform (Spring Boot, modular)
                                          ├── identity
                                          ├── servicedesk
                                          ├── sla
                                          ├── workflow
                                          ├── asset
                                          ├── knowledge
                                          └── audit
                                              │
                                   PostgreSQL · Redis · Kafka
                                              │
                                     AI Service (FastAPI + Qdrant)
```

---

## Technology

| Layer | Technology |
|---|---|
| Frontend | Next.js 15, React 19, TypeScript, Tailwind, TanStack Query |
| Backend | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, Maven |
| Data | PostgreSQL 17 (Row-Level Security), Redis 7, Apache Kafka |
| AI | Python 3.12, FastAPI, Qdrant, sentence-transformers |
| Infrastructure | Docker, Kubernetes (EKS), Terraform, GitHub Actions |
| Observability | Prometheus, Grafana, OpenTelemetry |

---

## Repository layout

```
docs/               Architecture decisions, diagrams, API specs, security, runbooks
services/           Backend services (Phase 0-3: one modular monolith)
frontend/           Next.js enterprise portal
deployment/         Docker Compose (local) and Kubernetes manifests
infrastructure/     Terraform modules and environments
.github/            CI workflows and repository governance
```

---

## Getting started

**Prerequisites**

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Docker Desktop | 28 or newer |
| Node.js | 20 LTS or newer (Phase 0 Part 6 onwards) |
| Python | 3.12+ (Phase 5 onwards) |

**1. Start the backing services**

```bash
cd deployment/docker
cp .env.example .env
docker compose up -d
docker compose ps          # all four should report (healthy)
```

| | |
|---|---|
| Mail inbox | <http://localhost:58025> |
| Database browser | <http://localhost:58080> |

See [deployment/docker/README.md](deployment/docker/README.md) for details.

**2. Build the backend**

```bash
cd services/platform
./mvnw clean verify        # on Windows: mvnw.cmd clean verify
```

See [services/platform/README.md](services/platform/README.md) for the module layout
and what each quality gate enforces.

**3. Run the backend**

```bash
./mvnw -pl platform-app spring-boot:run
```

<http://localhost:8080/actuator/health> should report `db` and `redis` as `UP`.

**4. Run the web portal**

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Open <http://localhost:3000> — the dashboard shows live backend health.

See [frontend/README.md](frontend/README.md) for why the browser never calls Spring
directly.

---

## Continuous integration

Three workflows, path-filtered so a frontend-only change does not rebuild Java.

| Workflow | Trigger | Does |
|---|---|---|
| [backend.yml](.github/workflows/backend.yml) | `services/platform/**` | Enforcer → Spotless → Checkstyle → unit tests → integration tests on real containers → coverage → SBOM → vulnerability scan |
| [frontend.yml](.github/workflows/frontend.yml) | `frontend/**` | Prettier → ESLint → `tsc --noEmit` → production build |
| [security.yml](.github/workflows/security.yml) | every PR, plus weekly | Gitleaks over full history, frontend dependency scan, config scan, CodeQL |

Everything CI runs can be run locally with the same commands — there is no CI-only step.

**Dependency scanning uses an SBOM.** The backend build emits a CycloneDX bill of
materials, and Trivy scans that rather than the source tree. Pointing a scanner at a Maven
project makes it resolve every POM from Maven Central, which is slow and gets rate-limited.
The SBOM already holds the resolved tree, so the scan is offline and instant — and the
artifact is retained for 90 days, so "what exactly shipped in that build?" has an answer.

> The weekly schedule matters more than it looks: dependencies stop changing, but the list
> of known vulnerabilities does not.

**Notes.** CodeQL is skipped on private repositories, where it needs GitHub Advanced
Security; it runs automatically if the repository is made public. Gitleaks Action is free
for personal accounts and requires a licence only for organisation-owned repositories.

---

## Documentation

| Document | Purpose |
|---|---|
| [PLAN.md](PLAN.md) | Full delivery plan, phases and exit criteria |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Branching, commit format, Definition of Done |
| [docs/adr/](docs/adr/) | Architecture Decision Records |
| [docs/architecture/](docs/architecture/) | C4 diagrams and the event catalogue |
| [docs/requirements/](docs/requirements/) | The original project brief |

---

## Licence

Private portfolio project. Not licensed for redistribution.
