# ADR-0003: Build the identity service in-house rather than adopting Keycloak

| | |
|---|---|
| **Status** | Accepted |
| **Date** | 2026-09-18 |
| **Deciders** | Isura Perera |
| **Supersedes** | None |

---

## Context

OpsPilot needs authentication and authorization: registration, email verification,
login, password reset, JWT issuance, refresh tokens, role- and permission-based access
control, account lockout, rate limiting and tenant isolation.

Mature solutions exist for all of this. Keycloak is open source, self-hostable,
implements OAuth 2.1 and OpenID Connect correctly, and supports multi-tenancy through
realms. AWS Cognito and Microsoft Entra ID are managed equivalents.

The standard engineering advice is unambiguous: **do not write your own authentication.**
It is security-critical, easy to get subtly wrong, and thoroughly solved.

That advice is correct, and this decision goes against it deliberately. The reason is
that OpsPilot has a second objective alongside being a working system: it is a
portfolio project whose purpose is to demonstrate depth in exactly the areas that
delegating authentication would hide.

## Decision

> We will implement authentication and authorization in-house using **Spring Security**,
> in a dedicated `identity` module that becomes a standalone service in Phase 4.
>
> **In a commercial setting the correct decision would be Keycloak.** This ADR exists so
> that the trade-off is on the record and is understood, rather than appearing to be an
> oversight.

### What is built

| Capability | Implementation |
|---|---|
| Password storage | BCrypt, cost factor 12, with a configurable complexity policy |
| Access tokens | Short-lived JWT, 15 minutes, signed RS256, tenant and permissions as claims |
| Refresh tokens | Opaque, single-use, **rotated on every use**, stored hashed in Redis |
| Token theft detection | Token families — reusing a consumed refresh token revokes the entire family |
| Brute-force defence | Account lockout with exponential backoff, plus per-IP and per-account rate limiting in Redis |
| Authorization | Permission-based (`TICKET_ASSIGN`), with roles as permission bundles |
| Enforcement point | The service layer, via `@PreAuthorize` — never the controller alone |
| Tenant isolation | `tenant_id` claim, verified, feeding `TenantContext` — see [ADR-0002](0002-multi-tenancy-strategy.md) |

### Boundaries we will not cross

To keep the risk proportionate, the following are explicitly **out of scope**. Each is
a genuine specialism where a hand-rolled implementation would be indefensible:

- Acting as an OAuth 2.0 authorization server for third-party clients
- Federated identity, SAML, or enterprise SSO
- Issuing tokens consumed by systems outside OpsPilot

The identity service authenticates OpsPilot's own users for OpsPilot's own services.
Nothing beyond that.

### Migration path

Authentication is consumed through an internal `AuthenticationProvider` interface and
tokens are validated at the API gateway. Should this decision be reversed, the change is
contained: swap the token issuer for Keycloak, point the gateway at Keycloak's JWKS
endpoint, and migrate the user store. The application's authorization logic, which reads
permissions from validated claims, is unaffected.

## Consequences

### Positive

- Demonstrable, reviewable implementation of the security concepts that matter in
  interviews: token lifecycle, rotation, reuse detection, permission modelling, lockout,
  rate limiting and tenant isolation
- No dependency on an external identity provider running in every developer and CI
  environment — Keycloak adds a container, a realm import and a startup delay to every
  test run
- Complete control over the user model, so `tenant_id` and the permission model sit
  naturally in the same schema as the rest of the platform
- The RS256 signing and JWKS approach mirrors what Keycloak would do, so the rest of the
  system is written against a standard interface either way

### Negative

- **This is the highest-risk decision in the project.** Authentication bugs are security
  incidents, not defects. Mitigated by: RS256 over HS256, rotation with reuse detection,
  a documented threat model, dedicated integration tests for every attack path, and
  CodeQL plus dependency scanning in CI
- Meaningful engineering time spent on a solved problem — roughly two weeks that could
  have gone to business features
- Features that come free with Keycloak (SSO, MFA, social login, an admin console,
  compliance certifications) are absent, and adding any of them later is substantial work
- A reviewer who does not read this ADR may reasonably judge the choice as naive. The
  ADR is the mitigation; it must be easy to find from the README

### Neutral

- The identity module is the largest module in Phase 1 and sets the code conventions the
  rest of the project follows
- Email delivery (verification, password reset) is needed from Phase 1, which pulls
  Mailpit into the local environment earlier than it otherwise would be

## Alternatives considered

### Alternative A — Keycloak

The correct production answer. Battle-tested, standards-compliant, realms map cleanly to
tenants, and it removes an entire category of risk. Rejected **only** because delegating
authentication would remove the security implementation that this project is partly
intended to demonstrate. If OpsPilot were a commercial product, this would be the choice.

### Alternative B — AWS Cognito

Managed, integrates with the intended AWS deployment, no operational burden. Rejected
for the same reason as Keycloak, with the additional drawback of cloud vendor lock-in and
the inability to run it locally without emulation.

### Alternative C — Spring Authorization Server

The official Spring project for building an OAuth 2.1 authorization server. A reasonable
middle path — standards-compliant, but still implemented by us. Rejected because full
OAuth 2.1 flows are unnecessary for a first-party application with a single web client,
and the added protocol surface would obscure the parts worth demonstrating. It is the
natural upgrade if third-party client access is ever required.

## References

- OWASP Authentication Cheat Sheet
- OWASP Session Management Cheat Sheet
- RFC 6749 §10 — OAuth 2.0 Security Considerations
- IETF OAuth 2.0 Security Best Current Practice — refresh token rotation
- [ADR-0002](0002-multi-tenancy-strategy.md) — tenant claim handling
- [PLAN.md](../../PLAN.md) section 1 Decision B, Phase 1
