# ADR-0002: Multi-tenancy via shared schema with a discriminator and Row-Level Security

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-09-18 |
| **Deciders** | Isura Perera |
| **Supersedes** | None |

---

## Context

OpsPilot is a multi-tenant platform: one deployment serves several customer
organisations. Company A and Company B both have users, teams, tickets and assets in
the same system, and a user from Company A must never be able to see Company B's data
under any circumstance — including a mistyped query, a missing `WHERE` clause or a
crafted request.

There are three established approaches:

| Approach | Isolation | Operational cost | Per-tenant scaling |
|---|---|---|---|
| Database per tenant | Strongest | Highest — N databases to migrate, back up, monitor | Excellent |
| Schema per tenant | Strong | High — N schemas, migrations run N times | Good |
| Shared schema + discriminator | Weakest by default | Lowest — one schema, one migration | Poor |

The weakness of the shared-schema approach is specific and well understood: isolation
depends entirely on every query being correctly filtered. One repository method that
forgets `AND tenant_id = ?` is a data breach.

This decision also has to be made before Phase 1, because retrofitting a tenant
discriminator onto an existing schema means touching every table, every query and every
test.

## Decision

> We will use a **shared database with a shared schema and a `tenant_id` discriminator
> column**, and we will compensate for its weakness with **two independent enforcement
> layers**: a Hibernate tenant filter in the application, and PostgreSQL Row-Level
> Security in the database.

### Layer 1 — Application

- A servlet filter reads the verified JWT and populates a `TenantContext` held in a
  `ThreadLocal` for the duration of the request.
- Hibernate's `@TenantId` / global filter automatically appends the tenant predicate to
  every query against a tenant-scoped entity.
- `TenantContext` is cleared in a `finally` block, so a pooled thread can never carry a
  previous request's tenant.

### Layer 2 — Database

- Every tenant-scoped table has `ROW LEVEL SECURITY` enabled with a `FORCE` policy.
- The policy compares `tenant_id` against a session variable, `app.current_tenant`, set
  on the connection at the start of each transaction.
- The application connects as a role **without** `BYPASSRLS`. Flyway migrations use a
  separate, more privileged role.

If the application layer fails, the database returns zero rows. Two independent
mechanisms must both fail for data to leak, and they fail for different reasons.

### Supporting rules

1. `tenant_id` is resolved **only** from the cryptographically verified JWT. Never from
   a path variable, query parameter, header or request body. A tenant identifier that
   the caller can choose is not a security control.
2. Cross-tenant access returns **404, not 403**. A 403 confirms that the resource
   exists, which leaks information about another tenant.
3. Every module's integration test suite includes a cross-tenant isolation test. This
   is part of the Definition of Done, not an optional extra.
4. The AI service applies the tenant filter **inside** the Qdrant query as a payload
   filter, never as a post-filter on returned results. Filtering after retrieval means
   another tenant's documents were already loaded into memory and may have influenced
   the ranking.
5. Background jobs and Kafka consumers run without a request context, so they must set
   `TenantContext` explicitly per message. Any job that processes all tenants iterates
   tenant by tenant rather than issuing an unscoped query.

## Consequences

### Positive

- One schema to migrate, one database to back up, monitor and tune — the operational
  cost stays flat as tenants are added
- Onboarding a new tenant is an `INSERT`, not a provisioning workflow
- Cross-tenant analytics and platform-wide reporting remain simple queries
- Defence in depth: an application bug is contained by the database, which is a
  genuinely strong security posture and a good interview discussion
- Resource cost is a fraction of database-per-tenant, which matters for a project
  running on a personal AWS account

### Negative

- Row-Level Security adds a predicate to every query, with a measurable planning and
  execution cost. Mitigated by putting `tenant_id` first in composite indexes
- Every connection must have the session variable set before use. With a connection
  pool this is easy to get wrong; it is handled once in a central `DataSource`
  decorator rather than being left to individual services
- A single noisy tenant can affect the shared database's performance. Accepted at this
  scale; the mitigation, if ever needed, is to move that tenant to its own database
- A bug that leaks data leaks it across all tenants at once, not one. This is precisely
  why two layers exist
- Per-tenant point-in-time restore is difficult — restoring one tenant's data means a
  selective export rather than a database restore

### Neutral

- Every tenant-scoped table carries an extra column and an extra index
- Tests require at least two seeded tenants to be meaningful

## Alternatives considered

### Alternative A — Database per tenant

The strongest isolation: a query cannot cross a boundary that does not exist in the
connection. Rejected because migrations must run against every database, connection
pools multiply, and cross-tenant reporting requires federation. The operational burden
grows linearly with tenants, and for a project of this size that cost is not repaid.

Worth noting: this is the correct choice when tenants are few, large, and contractually
demand physical isolation — for example regulated healthcare or finance customers.

### Alternative B — Schema per tenant

A middle ground. Rejected for similar reasons: migrations still run N times, PostgreSQL
performance degrades with very large numbers of schemas, and the connection routing
logic is nearly as complex as database-per-tenant without matching its isolation
guarantee.

### Alternative C — Shared schema with application-layer filtering only

The common implementation, and the one this decision deliberately improves on. Rejected
as insufficient on its own: it makes correctness depend on every developer remembering
a filter on every query forever. Row-Level Security converts that from a discipline
problem into an enforced invariant, at a cost of a few lines per migration.

## References

- PostgreSQL documentation — Row Security Policies
- Hibernate 6 documentation — `@TenantId` and multi-tenancy
- OWASP — Broken Object Level Authorization (API1:2023)
- [ADR-0001](0001-modular-monolith.md) — module boundaries that this must survive
- [PLAN.md](../../PLAN.md) section 5
