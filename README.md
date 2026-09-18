# OpsPilot

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
| Backend | Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Maven |
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

> Not runnable yet — the local environment arrives in Part 4 of Phase 0.

**Prerequisites**

| Tool | Version |
|---|---|
| JDK | 21 (Temurin recommended) |
| Node.js | 20 LTS or newer |
| Docker Desktop | latest |
| Python | 3.12 (Phase 5 onwards) |

```bash
git clone <repository-url> ops-pilot
cd ops-pilot
# Instructions land here as each part is built.
```

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
