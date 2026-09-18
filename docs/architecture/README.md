# OpsPilot Architecture

Documented using the [C4 model](https://c4model.com/): four levels of zoom, each aimed at
a different audience. Diagrams are Mermaid source so they live in Git, diff like code, and
render directly on GitHub.

| Level | Question it answers | Audience |
|---|---|---|
| 1 — Context | What is the system and who uses it? | Everyone |
| 2 — Container | What are the deployable pieces? | Technical |
| 3 — Component | What is inside a container? | Developers |
| 4 — Code | How is a component implemented? | Covered by the code itself |

> **Current state: Phase 0.** These diagrams describe the target for Phases 0–3. The
> Phase 4 target state is shown separately at the end.

---

## Level 1 — System Context

Who uses OpsPilot and what it depends on.

```mermaid
flowchart TB
    employee["👤 Employee<br/><i>Reports problems,<br/>requests services</i>"]
    agent["👤 Support Agent<br/><i>Resolves tickets</i>"]
    lead["👤 Team Lead<br/><i>Assigns work,<br/>monitors SLAs</i>"]
    admin["👤 Administrator<br/><i>Configures the platform</i>"]

    opspilot["<b>OpsPilot</b><br/>Enterprise Service<br/>Management Platform<br/><br/><i>Multi-tenant ITSM with<br/>an AI support assistant</i>"]

    email["✉️ Email Provider<br/><i>Amazon SES</i>"]
    llm["🤖 LLM Provider<br/><i>Hosted API</i>"]
    storage["🗄️ Object Storage<br/><i>Amazon S3</i>"]

    employee -->|"Raises tickets,<br/>asks the assistant"| opspilot
    agent -->|"Works the queue"| opspilot
    lead -->|"Routes and escalates"| opspilot
    admin -->|"Manages users,<br/>SLAs, categories"| opspilot

    opspilot -->|"Sends notifications"| email
    opspilot -->|"Generates grounded<br/>answers"| llm
    opspilot -->|"Stores attachments"| storage

    classDef person fill:#0B5394,stroke:#073763,color:#fff
    classDef system fill:#1168BD,stroke:#0B4884,color:#fff,font-size:14px
    classDef external fill:#6B7280,stroke:#4B5563,color:#fff

    class employee,agent,lead,admin person
    class opspilot system
    class email,llm,storage external
```

### The four roles

| Role | Can do |
|---|---|
| **Employee** | Create and view own tickets, comment, attach files, search the knowledge base, use the AI assistant, approve requests assigned to them |
| **Support Agent** | Everything above, plus: view and pick up queue tickets, change status, add internal notes, resolve |
| **Team Lead** | Everything above, plus: view all team tickets, assign and reassign, monitor SLAs, escalate, view team analytics |
| **Administrator** | Manage users, teams, roles, categories, SLA policies, assets, knowledge base; view audit logs |

Permissions are fine-grained (`TICKET_ASSIGN`, `AUDIT_VIEW`); roles are named bundles of
permissions. See [ADR-0003](../adr/0003-in-house-identity-service.md).

---

## Level 2 — Containers (Phases 0–3)

The deployable pieces and the data stores behind them.

```mermaid
flowchart TB
    user["👤 User<br/><i>Browser</i>"]

    subgraph edge [" "]
        web["<b>Web Portal</b><br/>Next.js 15 · React 19<br/><i>Dashboards, ticket views,<br/>AI chat</i>"]
    end

    subgraph backend ["Backend"]
        platform["<b>Platform</b><br/>Spring Boot 3.5 · Java 21<br/><i>Modular monolith —<br/>see Level 3</i>"]
        ai["<b>AI Service</b><br/>FastAPI · Python 3.12<br/><i>RAG, classification,<br/>summarisation</i><br/><b>Phase 5</b>"]
    end

    subgraph data ["Data"]
        pg[("<b>PostgreSQL 17</b><br/><i>Tenants, users, tickets,<br/>assets, audit<br/>Row-Level Security</i>")]
        redis[("<b>Redis 7</b><br/><i>Refresh tokens,<br/>rate limits, cache</i>")]
        qdrant[("<b>Qdrant</b><br/><i>Knowledge and ticket<br/>embeddings</i><br/><b>Phase 5</b>")]
    end

    email["✉️ SES"]
    llm["🤖 LLM API"]
    s3["🗄️ S3"]

    user -->|HTTPS| web
    web -->|"JSON / HTTPS<br/>JWT bearer"| platform
    web -.->|"WebSocket / STOMP<br/>live ticket updates"| platform
    web -->|"JSON / HTTPS"| ai

    platform --> pg
    platform --> redis
    platform --> s3
    platform -->|"SMTP"| email
    platform -.->|"Ticket and article text<br/>for indexing"| ai

    ai --> qdrant
    ai --> llm
    ai -.->|"Reads permitted<br/>articles and tickets"| platform

    classDef person fill:#0B5394,stroke:#073763,color:#fff
    classDef container fill:#1168BD,stroke:#0B4884,color:#fff
    classDef store fill:#0F766E,stroke:#115E59,color:#fff
    classDef external fill:#6B7280,stroke:#4B5563,color:#fff

    class user person
    class web,platform,ai container
    class pg,redis,qdrant store
    class email,llm,s3 external
```

Dotted lines are asynchronous or deferred to a later phase.

---

## Level 3 — Components inside the Platform

The modules of the monolith. Each is a separate Maven module with an enforced boundary,
and each becomes a candidate microservice in Phase 4. See
[ADR-0001](../adr/0001-modular-monolith.md).

```mermaid
flowchart TB
    api["<b>Web / API layer</b><br/><i>Controllers, filters,<br/>exception handling</i>"]

    subgraph modules ["Domain modules"]
        identity["<b>identity</b><br/>Tenants · Users<br/>Roles · Permissions<br/>Tokens"]
        servicedesk["<b>servicedesk</b><br/>Tickets · Comments<br/>Attachments · Teams<br/>Categories"]
        sla["<b>sla</b><br/>Policies · Clocks<br/>Business calendar<br/>Breach detection"]
        workflow["<b>workflow</b><br/>Approval chains<br/>Steps · Decisions"]
        asset["<b>asset</b><br/>Registry · Assignments<br/>Lifecycle"]
        knowledge["<b>knowledge</b><br/>Articles · Versions<br/>Tags"]
        audit["<b>audit</b><br/>Append-only<br/>event log"]
    end

    common["<b>common</b><br/><i>Shared kernel — errors, TenantContext,<br/>domain event base types, utilities</i>"]
    bus{{"<b>Domain Event Bus</b><br/><i>In-process today,<br/>Kafka in Phase 4</i>"}}

    api --> identity
    api --> servicedesk
    api --> asset
    api --> knowledge
    api --> audit

    servicedesk --> bus
    workflow --> bus
    asset --> bus

    bus --> sla
    bus --> audit
    bus --> workflow

    identity -.-> common
    servicedesk -.-> common
    sla -.-> common
    workflow -.-> common
    asset -.-> common
    knowledge -.-> common
    audit -.-> common

    classDef entry fill:#1168BD,stroke:#0B4884,color:#fff
    classDef module fill:#2563EB,stroke:#1D4ED8,color:#fff
    classDef shared fill:#7C3AED,stroke:#5B21B6,color:#fff
    classDef bus fill:#B45309,stroke:#92400E,color:#fff

    class api entry
    class identity,servicedesk,sla,workflow,asset,knowledge,audit module
    class common shared
    class bus bus
```

### The five boundary rules

Enforced by ArchUnit tests. A violation fails the build.

1. A module exposes only its `api` package — everything else is internal
2. No cross-module JPA relationships — reference by ID, never `@ManyToOne`
3. No cross-module foreign keys in the database
4. Modules talk through published interfaces or domain events, never another module's repository
5. The domain layer depends on nothing — no Spring, no JPA, no HTTP

Rules 2 and 3 are the critical pair. A foreign key across a module boundary is exactly
what makes a database impossible to split later.

---

## Ticket lifecycle

The core state machine. Transitions are defined in an explicit table in the domain layer,
so an illegal transition such as `CLOSED → IN_PROGRESS` is rejected by the domain model
rather than by a controller check.

```mermaid
stateDiagram-v2
    [*] --> OPEN: employee creates

    OPEN --> TRIAGED: categorised, priority set
    OPEN --> CANCELLED: withdrawn

    TRIAGED --> IN_PROGRESS: agent picks up
    TRIAGED --> CANCELLED: withdrawn

    IN_PROGRESS --> WAITING_FOR_USER: needs information
    IN_PROGRESS --> WAITING_FOR_APPROVAL: needs sign-off
    IN_PROGRESS --> RESOLVED: fix applied

    WAITING_FOR_USER --> IN_PROGRESS: employee responds
    WAITING_FOR_APPROVAL --> IN_PROGRESS: approved
    WAITING_FOR_APPROVAL --> CANCELLED: rejected

    RESOLVED --> CLOSED: confirmed or auto-closed
    RESOLVED --> IN_PROGRESS: employee reopens

    CLOSED --> [*]
    CANCELLED --> [*]
```

**The SLA clock pauses in `WAITING_FOR_USER`.** Time spent waiting on the reporter must
not count against the resolution target, or every blocked ticket produces a false breach.

---

## Domain events

Published by the module that owns the change; consumed by any module that needs to react.
Spring application events in Phases 0–3, the same classes over Kafka from Phase 4.

| Event | Published by | Consumed by |
|---|---|---|
| `TicketCreated` | servicedesk | sla, audit, notification, ai |
| `TicketAssigned` | servicedesk | notification, audit, analytics |
| `TicketStatusChanged` | servicedesk | sla, audit, notification |
| `TicketPriorityChanged` | servicedesk | sla, audit |
| `TicketCommentAdded` | servicedesk | notification, audit |
| `TicketResolved` | servicedesk | sla, audit, notification, knowledge |
| `TicketClosed` | servicedesk | analytics, audit |
| `SlaWarningRaised` | sla | notification, analytics |
| `SlaBreached` | sla | notification, analytics, audit |
| `ApprovalRequested` | workflow | notification, audit |
| `ApprovalDecided` | workflow | servicedesk, notification, audit |
| `AssetAssigned` | asset | audit, notification |
| `KnowledgeArticlePublished` | knowledge | ai (re-index) |

Every event carries `eventId`, `tenantId`, `occurredAt` and `actorId`. `eventId` is what
makes Phase 4 consumers idempotent.

---

## Request flow — creating a ticket

```mermaid
sequenceDiagram
    autonumber
    participant U as Employee
    participant W as Web Portal
    participant A as API layer
    participant I as identity
    participant S as servicedesk
    participant B as Event bus
    participant L as sla
    participant D as audit

    U->>W: Submit ticket form
    W->>A: POST /api/v1/tickets (JWT)
    A->>I: Validate token, resolve tenant
    I-->>A: userId, tenantId, permissions
    A->>A: Set TenantContext
    A->>S: createTicket(command)
    S->>S: Validate, apply initial state OPEN
    S->>S: Persist (RLS enforces tenant)
    S->>B: publish TicketCreated
    S-->>A: TicketResponse
    A-->>W: 201 Created
    W-->>U: Ticket #10482

    B->>L: TicketCreated
    L->>L: Resolve SLA policy, start clock
    B->>D: TicketCreated
    D->>D: Append audit record
```

Note step 5: the tenant comes from the **validated token**, never from the request body.
See [ADR-0002](../adr/0002-multi-tenancy-strategy.md).

---

## Phase 4 target — distributed architecture

What the modules become once extracted. Nothing in the domain code changes; the transport
between modules does.

```mermaid
flowchart TB
    web["<b>Web Portal</b><br/>Next.js"]
    gw["<b>API Gateway</b><br/>Spring Cloud Gateway<br/><i>Routing · JWT validation<br/>Rate limiting · CORS</i>"]

    subgraph svc ["Services"]
        idsvc["<b>identity-service</b>"]
        tksvc["<b>ticket-service</b><br/><i>+ sla, workflow,<br/>knowledge</i>"]
        assvc["<b>asset-service</b>"]
        ntsvc["<b>notification-service</b>"]
        ansvc["<b>analytics-service</b>"]
        aisvc["<b>ai-service</b><br/><i>FastAPI</i>"]
    end

    kafka{{"<b>Apache Kafka</b><br/><i>Versioned event contracts<br/>Transactional outbox<br/>Idempotent consumers · DLQ</i>"}}

    idb[("identity<br/>DB")]
    tdb[("ticket<br/>DB")]
    adb[("asset<br/>DB")]
    andb[("analytics<br/>read model")]
    qd[("Qdrant")]
    rd[("Redis")]

    web --> gw
    gw --> idsvc
    gw --> tksvc
    gw --> assvc
    gw --> ansvc
    gw --> aisvc

    idsvc --> idb
    idsvc --> rd
    tksvc --> tdb
    assvc --> adb
    ansvc --> andb
    aisvc --> qd

    tksvc --> kafka
    assvc --> kafka
    idsvc --> kafka
    kafka --> ntsvc
    kafka --> ansvc
    kafka --> aisvc

    classDef container fill:#1168BD,stroke:#0B4884,color:#fff
    classDef store fill:#0F766E,stroke:#115E59,color:#fff
    classDef broker fill:#B45309,stroke:#92400E,color:#fff

    class web,gw,idsvc,tksvc,assvc,ntsvc,ansvc,aisvc container
    class idb,tdb,adb,andb,qd,rd store
    class kafka broker
```

`sla`, `workflow` and `knowledge` stay inside `ticket-service`. They are tightly coupled to
the ticket aggregate and share its transaction boundary — splitting them would buy
distributed transactions for no benefit. **Six services, not fifteen.**

---

## Related documents

| Document | Contents |
|---|---|
| [PLAN.md](../../PLAN.md) | Delivery plan, phases, exit criteria |
| [docs/adr/](../adr/) | Why each decision was made |
| [CONTRIBUTING.md](../../CONTRIBUTING.md) | The boundary rules as enforced practice |
