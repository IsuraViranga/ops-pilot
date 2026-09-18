# ADR-0001: Start as a modular monolith, extract microservices later

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-09-18 |
| **Deciders** | Isura Perera |
| **Supersedes** | None |

---

## Context

OpsPilot is targeted at a microservice architecture: identity, ticketing, assets,
notifications, analytics and AI as independent services communicating over Kafka.
That is the end state described in [PLAN.md](../../PLAN.md) Phase 4.

The question is not *whether* to get there, but *when to start*.

Building six services on day one carries costs that arrive before any business value:

- Six build pipelines, six container images, six deployment manifests
- Six databases, with no ability to use a transaction across them
- Service discovery, inter-service authentication, retries and circuit breakers
- Distributed tracing required just to answer "why did this request fail?"
- Local development needing six processes running to test one feature

Every one of those is real engineering work, and none of it makes a ticket get
created. For a project built outside of working hours, that ordering is what
determines whether it is ever finished.

At the same time, the alternative failure mode is well known: a "monolith" written
without boundaries becomes a ball of mud, and the microservice extraction that was
always planned turns into a rewrite that never happens.

## Decision

> We will build OpsPilot as a **single Spring Boot application composed of independent
> Maven modules**, with module boundaries enforced automatically at build time. We will
> extract modules into separate services in Phase 4, once the domain has stabilised.

The boundaries are the point. They are enforced by five rules, checked by ArchUnit
tests that fail the build on violation:

1. **Each module exposes only an `api` package.** All other packages are internal and
   unreachable from outside the module.
2. **No cross-module JPA relationships.** A ticket references a user by `userId`, never
   by `@ManyToOne User`.
3. **No cross-module foreign keys** in the database schema.
4. **Modules communicate through published interfaces or domain events**, never by
   calling another module's repository directly.
5. **The domain layer depends on nothing** — no Spring, no JPA, no HTTP annotations on
   pure domain classes.

Rules 2 and 3 are the critical ones. A foreign key between two modules' tables is
exactly the thing that makes a database impossible to split later.

Domain events use Spring's `ApplicationEventPublisher` in Phase 0–3. In Phase 4 the
same event classes are published to Kafka instead. Because publishers and listeners
already communicate only through event objects, the domain code does not change —
only the transport does.

## Consequences

### Positive

- Business functionality can be built immediately, without infrastructure prerequisites
- One process to run locally; one debugger; one stack trace when something breaks
- Real database transactions across the whole request, so no saga or compensating
  transaction complexity until it is genuinely needed
- Refactoring across module boundaries is a compile-time operation while the domain is
  still moving, rather than a coordinated multi-service deployment
- The Phase 4 extraction becomes a mechanical exercise: move a module into its own
  repository, replace in-process events with Kafka, replace interface calls with HTTP
- The migration itself becomes a demonstrable engineering achievement rather than an
  initial assumption

### Negative

- The boundary rules must be genuinely enforced. If ArchUnit is disabled or its
  violations are suppressed, the whole rationale collapses and this becomes an ordinary
  monolith with a microservices story attached to it
- Modules cannot be scaled or deployed independently until Phase 4
- A single shared database means a badly written query in one module can affect all
  modules — mitigated by connection pool limits and query timeouts
- A reader skimming the repository may assume "monolith" means "no distributed systems
  experience". Mitigated by this ADR and by actually completing Phase 4

### Neutral

- The module structure mirrors the intended service structure one-to-one, so the
  architecture diagrams stay accurate across the transition
- Tests are split into fast unit tests and slower Testcontainers integration tests from
  the outset, which is required either way

## Alternatives considered

### Alternative A — Microservices from day one

The original plan. Rejected because the operational overhead would consume the first
several weeks and delay the first working feature past the point where the project
typically stalls. It also fixes service boundaries before the domain is understood,
and a wrongly placed boundary between two services is far more expensive to correct
than one between two modules.

### Alternative B — Unstructured monolith, refactor later

Rejected because "we will add the boundaries later" reliably becomes "we never added
the boundaries". Once cross-module foreign keys and JPA relationships exist, extracting
a service means untangling the schema, which is the expensive part. The boundaries cost
very little to maintain from the start and almost nothing can be salvaged by deferring
them.

### Alternative C — Spring Modulith

A Spring project that provides exactly this pattern with built-in verification and
event publication registry. Genuinely a good fit. Rejected for this project because
hand-rolling the boundary enforcement with separate Maven modules and explicit ArchUnit
rules makes the architecture visible in the repository rather than delegated to a
framework — which is the point for a portfolio project. Spring Modulith would be the
correct choice in a commercial setting.

## References

- Martin Fowler — *MonolithFirst* and *Modular Monolith*
- Sam Newman — *Building Microservices*, 2nd edition, chapter on decomposition
- Simon Brown — *Modular Monoliths* talk
- [PLAN.md](../../PLAN.md) sections 1, 4 and Phase 4
