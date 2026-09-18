## What

<!-- What does this change do? One or two sentences. -->

## Why

<!-- What problem does it solve? Link the issue if there is one. -->

Closes #

## How

<!-- Notable implementation decisions a reviewer should understand before reading the diff. -->

## Definition of Done

- [ ] Unit tests added, suite green
- [ ] Integration tests added where the change crosses a boundary
- [ ] Flyway migration written (if the schema changed)
- [ ] OpenAPI specification updated (if the API changed)
- [ ] ADR written (if a significant decision was made)
- [ ] Logging and metrics added for the new path
- [ ] Runs from a clean `docker compose up`
- [ ] README / runbook updated (if operational behaviour changed)

## Security

- [ ] No secrets committed
- [ ] Input validated at the API boundary
- [ ] Authorization checked at the service layer
- [ ] Tenant isolation holds (cross-tenant access returns 404)

## Screenshots / evidence

<!-- UI changes: before and after. Backend changes: a log line, test output or trace. -->
