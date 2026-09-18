# Architecture Decision Records

An ADR captures a single significant decision: the situation that forced it, what was
decided, what it costs, and what was rejected. It answers the question a future reader
will ask — *"why on earth did they do it that way?"*

ADRs are **immutable**. A decision is never edited to reflect a change of mind. Instead a
new ADR is written, and the old one is marked `Superseded by ADR-NNNN`. The history of
how thinking changed is as valuable as the current position.

---

## Index

| ADR | Title | Status | Date |
|---|---|---|---|
| [0001](0001-modular-monolith.md) | Start as a modular monolith, extract microservices later | Accepted | 2026-09-18 |
| [0002](0002-multi-tenancy-strategy.md) | Multi-tenancy via shared schema with a discriminator and Row-Level Security | Accepted | 2026-09-18 |
| [0003](0003-in-house-identity-service.md) | Build the identity service in-house rather than adopting Keycloak | Accepted | 2026-09-18 |

---

## Writing a new one

1. Copy [0000-template.md](0000-template.md) to `NNNN-short-title.md` using the next free number
2. Fill in every section — especially **Negative consequences** and **Alternatives considered**
3. Add a row to the index above
4. Include it in the pull request that implements the decision

## When an ADR is warranted

Write one if **any** of these is true:

- The decision is expensive or disruptive to reverse
- It affects more than one module or team
- It trades one desirable property away for another
- It goes against conventional advice — *especially* then
- A reasonable reviewer would question the choice

Do **not** write one for a decision that is obvious, local in scope, or trivially
reversible. An ADR directory full of noise is as useless as an empty one.

## Status values

| Status | Meaning |
|---|---|
| `Proposed` | Under discussion, not yet in effect |
| `Accepted` | In effect — the codebase reflects this |
| `Deprecated` | No longer applies, with nothing replacing it |
| `Superseded by ADR-NNNN` | Replaced by a later decision |

## Format

[MADR](https://adr.github.io/madr/) (Markdown Architecture Decision Records), lightly
adapted. The essential sections are **Context**, **Decision**, **Consequences** and
**Alternatives considered**.

An ADR that lists no downsides is not a decision record — it is an advertisement.
