# Feature Flag Tooling — Use Cases & Runtime Impact

Companion to `feature-flags-comparison.md`. For each of the five shortlisted tools, plus the in-house facade pattern from the final shortlist, this document gives:

- A **runtime & dependency diagram** — what components exist, how they talk to each other, and where the flag data lives.
- **Three concrete use cases**, each with a sequence diagram showing the call path at request time, plus notes on complexity, dependencies, and what happens in production if the flag backend becomes unavailable.

> Diagram legend: solid arrows are synchronous/blocking calls; dotted or "optional" labels are best-effort/async paths. Notes on sequence diagrams call out the failure-mode behavior explicitly, since that's the detail that matters most once this is running in production.

---

## Unleash

### Runtime & Dependency Architecture

```mermaid
flowchart TB
    subgraph vpc["AWS VPC"]
        subgraph services["Spring Boot Microservices"]
            SVC1["Checkout Service"]
            SVC2["Payments Service"]
            SVC3["Inventory Service"]
        end
        SDK1["Unleash Java SDK<br/>(in-process cache)"]
        SDK2["Unleash Java SDK<br/>(in-process cache)"]
        SDK3["Unleash Java SDK<br/>(in-process cache)"]
        US["Unleash Server (Node.js)"]
        PG[("PostgreSQL")]
    end
    UI["Admin UI (Engineers / PMs)"]

    SVC1 --> SDK1
    SVC2 --> SDK2
    SVC3 --> SDK3
    SDK1 -- "poll every ~15s" --> US
    SDK2 -- "poll every ~15s" --> US
    SDK3 -- "poll every ~15s" --> US
    US --> PG
    UI --> US
```

**Complexity:** Medium — one extra stateful service (Node.js + Postgres) to deploy and keep highly available, but the SDK-side story is simple.

---

### Use Case 1 — Canary rollout of a rewritten checkout service

Roll a new checkout implementation out by percentage of traffic (5% → 25% → 100%) using consistent user-ID hashing, with instant rollback via the UI if error rates spike.

```mermaid
sequenceDiagram
    participant Client
    participant Checkout as Checkout Service
    participant SDK as Unleash SDK (in-process)
    participant Server as Unleash Server
    participant DB as PostgreSQL

    Client->>Checkout: POST /checkout
    Checkout->>SDK: isEnabled("new-checkout-flow", userId)
    SDK-->>Checkout: true/false (served from local cache)
    Checkout-->>Client: response (old or new flow)

    par Background refresh
        SDK->>Server: GET /api/client/features
        Server->>DB: SELECT flag config
        DB-->>Server: rows
        Server-->>SDK: updated payload
    end

    Note over Server,DB: If Server/DB is down, SDK keeps serving<br/>the last cached rollout percentage — zero<br/>impact on live checkout traffic short-term.
```

- **Complexity:** Medium — requires defining a gradual-rollout strategy with a stickiness field (user ID).
- **Dependencies:** Unleash Server, PostgreSQL, one background polling connection per service instance.
- **Production impact if backend is unavailable:** Low. The traffic split freezes at the last fetched value; nothing fails open or closed unexpectedly.

---

### Use Case 2 — Kill-switch for a flaky downstream dependency

Wrap calls to a fragile third-party shipping-rate API behind a boolean flag. When the API misbehaves, an on-call engineer flips `shipping-api-enabled` off in the Unleash UI to fall back to a cached rate table across every `order-service` instance within one poll interval.

```mermaid
sequenceDiagram
    participant OnCall as On-call Engineer
    participant UI as Unleash Admin UI
    participant Server as Unleash Server
    participant DB as PostgreSQL
    participant SDK as Order Service SDKs (N instances)

    OnCall->>UI: Toggle "shipping-api-enabled" = false
    UI->>Server: PATCH /api/admin/features
    Server->>DB: UPDATE flag state
    loop every ~15s per instance
        SDK->>Server: GET /api/client/features
        Server-->>SDK: updated payload (flag = false)
    end
    Note over SDK: All instances now serve cached rate<br/>table instead of calling the flaky API.

    Note over OnCall,Server: Risk: if Unleash itself is down during<br/>the incident, this is not available as a<br/>mitigation path — engineer must fall back<br/>to a manual deploy or infra-level change.
```

- **Complexity:** Low — single boolean, no targeting rules.
- **Dependencies:** Unleash Server/DB for the toggle to propagate; propagation delay is bounded by each instance's poll interval.
- **Production impact if backend is unavailable:** Medium — this *is* an incident-response tool, so an Unleash outage during an incident removes your fastest lever.

---

### Use Case 3 — Environment-scoped promotion (dev → staging → prod)

A new fraud-detection rule is enabled in `dev`, validated in `staging` by QA, then promoted to `prod` for 100% of traffic — no redeploy, just a state change per environment.

```mermaid
sequenceDiagram
    participant Eng as Engineer
    participant UI as Unleash Admin UI
    participant Server as Unleash Server
    participant Dev as dev SDKs
    participant Stg as staging SDKs
    participant Prod as prod SDKs

    Eng->>UI: Enable flag in "development" environment
    UI->>Server: Update env-scoped state
    Server-->>Dev: flag = true (dev only)
    Note over Stg,Prod: staging & prod unaffected — env isolation

    Eng->>UI: Promote to "staging" after local testing
    UI->>Server: Update env-scoped state
    Server-->>Stg: flag = true

    Eng->>UI: Promote to "production" after QA sign-off
    UI->>Server: Update env-scoped state
    Server-->>Prod: flag = true
```

- **Complexity:** Medium-High — requires per-environment API keys/SDK configuration and a promotion discipline (manual or CI-driven).
- **Dependencies:** Unleash Server, PostgreSQL, correctly scoped SDK client keys per environment.
- **Production impact if backend is unavailable:** Low for already-promoted state (cached); the *promotion workflow itself* simply blocks until the server is back.

---

## Flagsmith

### Runtime & Dependency Architecture

```mermaid
flowchart TB
    subgraph vpc["AWS VPC"]
        subgraph services["Spring Boot Microservices"]
            SVC1["Billing Service"]
            SVC2["Notification Service"]
        end
        SDK1["Flagsmith Java SDK"]
        SDK2["Flagsmith Java SDK"]
        FS["Flagsmith Server (Django/Python)"]
        PG[("PostgreSQL")]
    end
    UI["Admin Dashboard"]

    SVC1 --> SDK1
    SVC2 --> SDK2
    SDK1 -- "per-request (default) OR polled if local-eval enabled" --> FS
    SDK2 -- "per-request (default) OR polled if local-eval enabled" --> FS
    FS --> PG
    UI --> FS
```

**Complexity:** Medium, with one important gotcha: the *default* server-SDK behavior is remote evaluation (a network call per flag check), not local caching — that has to be explicitly switched on.

---

### Use Case 1 — Per-tenant remote configuration in a multi-tenant SaaS

Each customer account has different plan-based limits (API rate limit, storage quota) stored as remote-config values, evaluated by identity/trait targeting rather than a single global flag.

```mermaid
sequenceDiagram
    participant Client
    participant Billing as Billing Service
    participant SDK as Flagsmith SDK
    participant FS as Flagsmith Server
    participant DB as PostgreSQL

    Client->>Billing: GET /usage-limits
    Billing->>SDK: getValue("api-rate-limit", identity=tenantId, traits)
    alt Remote evaluation (default)
        SDK->>FS: POST /api/v1/identities/ (per call)
        FS->>DB: lookup identity + traits + config
        DB-->>FS: rows
        FS-->>SDK: resolved value
    else Local evaluation (opt-in)
        SDK-->>SDK: resolve from cached environment doc
        Note over SDK,FS: Doc refreshed via periodic poll
    end
    SDK-->>Billing: rate limit value
    Billing-->>Client: 200 OK
```

- **Complexity:** Medium — identity + trait-based segmentation per tenant.
- **Dependencies:** Flagsmith Server, PostgreSQL, tenant trait data passed at evaluation time.
- **Production impact if backend is unavailable:** **High in the default (remote-eval) mode** — every flag check is a live call, so an outage can degrade or fail limit lookups on the request path. Switching the SDK to local-evaluation mode changes this to Low, same as Unleash's caching model.

---

### Use Case 2 — Beta feature rollout to a customer segment

A new analytics dashboard is enabled only for accounts on the "Enterprise" plan, or explicitly whitelisted beta accounts, using Flagsmith's segment/identity targeting.

```mermaid
sequenceDiagram
    participant PM as Product Manager
    participant UI as Flagsmith Dashboard
    participant FS as Flagsmith Server
    participant DB as PostgreSQL
    participant App as Analytics Service

    PM->>UI: Create segment "Enterprise plan OR beta-whitelist"
    UI->>FS: Save segment + flag override
    FS->>DB: persist segment rule
    App->>FS: (local-eval SDK) periodic poll for env config
    FS->>DB: fetch current segments + flags
    DB-->>FS: rows
    FS-->>App: updated config incl. segment rules
    Note over App: Segment matching evaluated locally,<br/>in-process, per request — no per-request<br/>network call once local-eval is enabled.
```

- **Complexity:** Low-Medium — segment definition is UI-driven, no code changes needed to add accounts.
- **Dependencies:** same as Use Case 1.
- **Production impact if backend is unavailable:** Low if local-eval mode is configured; High otherwise (same caveat as above).

---

### Use Case 3 — Incident-time config change without a redeploy

Ops adjusts a downstream HTTP timeout value (stored as remote config, not code) from 5s to 2s during an incident, without redeploying `notification-service`.

```mermaid
sequenceDiagram
    participant OnCall as On-call Engineer
    participant UI as Flagsmith Dashboard
    participant FS as Flagsmith Server
    participant DB as PostgreSQL
    participant Notif as Notification Service

    OnCall->>UI: Change "downstream-timeout-ms" 5000 -> 2000
    UI->>FS: PATCH config value
    FS->>DB: UPDATE value
    Notif->>FS: next poll / next request (per eval mode)
    FS-->>Notif: new value
    Note over OnCall,FS: If remote-eval mode and Flagsmith is the<br/>thing that's degraded, engineer can't read<br/>or write the very config needed to mitigate.
```

- **Complexity:** Low.
- **Dependencies:** Flagsmith Server/DB for the value to propagate.
- **Production impact if backend is unavailable:** Same eval-mode-dependent risk as Use Cases 1–2 — worth deciding local vs. remote evaluation deliberately before relying on this for incident response.

---

## GrowthBook

### Runtime & Dependency Architecture

```mermaid
flowchart TB
    subgraph vpc["AWS VPC"]
        subgraph services["Spring Boot Microservices"]
            SVC1["Pricing Service"]
            SVC2["API Gateway"]
        end
        SDK1["GrowthBook Java SDK"]
        SDK2["GrowthBook Java SDK"]
        GB["GrowthBook App (Node.js)"]
        MONGO[("MongoDB<br/>flag/experiment metadata")]
        WH[("PostgreSQL Warehouse<br/>event/analytics data")]
    end
    UI["Admin UI / Visual Editor"]

    SVC1 --> SDK1
    SVC2 --> SDK2
    SDK1 -- "poll cached config" --> GB
    SDK2 -- "poll cached config" --> GB
    GB --> MONGO
    GB -. "stats queries, async/offline" .-> WH
    UI --> GB
```

**Complexity:** Medium-High — adds MongoDB as a new database technology alongside your PostgreSQL, plus a warehouse integration if you want the stats engine.

---

### Use Case 1 — A/B test on checkout pricing display

Two variants of a pricing display are bucketed via consistent hashing in `pricing-service`; conversion events land in the PostgreSQL warehouse; GrowthBook's Bayesian stats engine computes significance and surfaces a winner.

```mermaid
sequenceDiagram
    participant Client
    participant Pricing as Pricing Service
    participant SDK as GrowthBook SDK (cached)
    participant GB as GrowthBook App
    participant Mongo as MongoDB
    participant WH as PostgreSQL Warehouse

    rect rgb(240,240,255)
    Note over Client,SDK: Hot path — request time
    Client->>Pricing: GET /price
    Pricing->>SDK: getFeatureValue("pricing-variant", userId)
    SDK-->>Pricing: "A" or "B" (from local cache)
    Pricing-->>Client: response
    end

    rect rgb(240,255,240)
    Note over Pricing,WH: Cold path — fully decoupled
    Pricing->>WH: emit conversion event (async)
    GB->>WH: query experiment results (on-demand/scheduled)
    WH-->>GB: aggregated metrics
    GB->>Mongo: store computed experiment stats
    end
```

- **Complexity:** Medium-High — needs an event-tracking pipeline into the warehouse plus experiment/metric definitions.
- **Dependencies:** GrowthBook App, MongoDB (metadata), PostgreSQL warehouse (results), an event pipeline.
- **Production impact if backend is unavailable:** Low. Bucketing is resolved locally from cached SDK config; the entire stats computation is async and off the request path.

---

### Use Case 2 — Rollout gated by a guardrail metric

A new recommendation algorithm rolls out to 10% of traffic; GrowthBook monitors an "error rate" guardrail metric from the warehouse and surfaces an alert if a regression is detected (OSS tier alerts, not automatic rollback).

```mermaid
sequenceDiagram
    participant Reco as Recommendation Service
    participant SDK as GrowthBook SDK (cached)
    participant WH as PostgreSQL Warehouse
    participant GB as GrowthBook App
    participant Eng as Engineer

    Reco->>SDK: getFeatureValue("new-reco-algo", userId)
    SDK-->>Reco: variant (10% bucket)
    Reco->>WH: emit error/latency events (async)
    loop scheduled evaluation
        GB->>WH: query guardrail metric
        WH-->>GB: metric value
        alt regression detected
            GB-->>Eng: alert (dashboard/notification)
        end
    end
```

- **Complexity:** High — guardrail metric definitions, warehouse query correctness, alert wiring.
- **Dependencies:** warehouse connection, GrowthBook App, MongoDB, SDK.
- **Production impact if backend is unavailable:** Low for the hot path (same caching model as Use Case 1); the guardrail *detection* itself pauses, which is a monitoring gap, not a request-path failure.

---

### Use Case 3 — No-code visual experiment on a marketing landing page

The growth/marketing team uses GrowthBook's visual editor to test two hero-section variants on the public marketing site, without engineering involvement, via the client-side JS SDK.

```mermaid
sequenceDiagram
    participant Visitor
    participant Page as Landing Page (JS SDK)
    participant GB as GrowthBook App
    participant Mongo as MongoDB

    Visitor->>Page: Load page
    Page->>GB: fetch experiment config (cached client-side)
    GB->>Mongo: load active experiments
    Mongo-->>GB: config
    GB-->>Page: variant assignment rules
    Page-->>Visitor: renders variant A or B locally

    Note over Page,GB: If GrowthBook is unreachable at load time,<br/>SDK falls back to the default variant<br/>defined in the embedded snippet.
```

- **Complexity:** Low for the marketing team (no-code); requires a one-time front-end integration point.
- **Dependencies:** GrowthBook App, MongoDB. Your Java backend is not in this particular loop.
- **Production impact if backend is unavailable:** Low — isolated to front-end rendering, with a defined default-variant fallback.

---

## FF4J

### Runtime & Dependency Architecture

```mermaid
flowchart TB
    subgraph app["Order Service (single Spring Boot app)"]
        APP["Application Code"]
        LIB["FF4J Library (embedded)"]
        CACHE["In-process cache (optional)"]
    end
    PG[("PostgreSQL<br/>ff4j_features table")]
    CONSOLE["FF4J Web Console<br/>(optional, self-hosted)"]

    APP --> LIB
    LIB --> CACHE
    LIB -- "JDBC read/write" --> PG
    CONSOLE -- "JDBC" --> PG
```

**Complexity:** Low per service — it's a library, not a server. Complexity shifts to the *fleet* level once more than one or two services share a flag store (see Use Case 3).

---

### Use Case 1 — Low-latency flags in a latency-sensitive pricing calculation

A single high-throughput pricing service needs flag checks with no network hop at all; FF4J is configured with an in-memory store so no external call happens during request handling.

```mermaid
sequenceDiagram
    participant Client
    participant Pricing as Pricing Service
    participant FF4J as FF4J (in-memory store)

    Client->>Pricing: GET /price
    Pricing->>FF4J: check("dynamic-pricing-v2")
    FF4J-->>Pricing: true/false (pure in-JVM lookup)
    Pricing-->>Client: response
    Note over FF4J: No network call, no DB call —<br/>flag state lives entirely in process memory.
```

- **Complexity:** Low — single service, no cross-service sync needed.
- **Dependencies:** None beyond the JVM if using an in-memory store; JDBC/Postgres only if persistence across restarts is required.
- **Production impact if Postgres is unavailable:** None for reads (in-memory store); if instead configured to hit JDBC per check with no cache, uncached reads would throw and need explicit fallback handling — an anti-pattern worth avoiding.

---

### Use Case 2 — Scheduled/time-based feature activation

A promotional banner and pricing rule auto-activate at a specific timestamp using FF4J's time-based flip strategy, with no deploy or manual toggle needed at go-live.

```mermaid
sequenceDiagram
    participant Scheduler as FF4J Time-based Strategy
    participant Store as Backing Store (JDBC)
    participant Web as Storefront Service
    participant Visitor

    Note over Scheduler: Strategy config: active 00:00–23:59 on sale date
    Visitor->>Web: GET /home
    Web->>Scheduler: check("holiday-sale-banner")
    Scheduler->>Scheduler: evaluate current time vs. configured window
    Scheduler-->>Web: true (within window) / false (outside)
    Web-->>Visitor: page with/without banner
```

- **Complexity:** Low-Medium — strategy configuration and timezone handling need care.
- **Dependencies:** FF4J library, backing store for the scheduled config.
- **Production impact if backend is unavailable:** Minimal; strategy evaluation is local/in-process once the config is loaded.

---

### Use Case 3 — Shared Postgres-backed table across a small service cluster

Four or five internal services (inventory, warehouse, fulfillment, shipping) each embed FF4J pointed at one shared `ff4j_features` table, with a self-hosted FF4J console for manual toggling.

```mermaid
sequenceDiagram
    participant Ops as Ops Engineer
    participant Console as FF4J Web Console
    participant DB as Shared PostgreSQL Table
    participant Inv as Inventory Svc (TTL 30s)
    participant Ful as Fulfillment Svc (TTL 5min)
    participant Ship as Shipping Svc (no cache)

    Ops->>Console: toggle "new-picking-algorithm" = true
    Console->>DB: UPDATE row

    Note over Inv,Ship: No central propagation mechanism —<br/>each service polls/caches independently.

    Inv->>DB: poll (every 30s)
    DB-->>Inv: true (within ~30s)
    Ful->>DB: poll (every 5min)
    DB-->>Ful: true (up to 5min stale)
    Ship->>DB: direct read every request
    DB-->>Ship: true (immediate, but DB load per request)
```

- **Complexity:** Medium — this is where the lack of a central server shows: every service owns its own cache/refresh policy, and there's no built-in propagation or streaming.
- **Dependencies:** shared PostgreSQL table, FF4J library per service, optional console deployment.
- **Production impact if Postgres is unavailable:** Inconsistent by design — depends entirely on each service's individual caching configuration. Services with no caching (like `Shipping` above) fail or block on every check; services with long TTLs keep serving stale-but-safe values. Standardizing cache policy across the fleet is a manual discipline, not something the tool enforces.

---

## GO Feature Flag (GOFF)

### Runtime & Dependency Architecture

```mermaid
flowchart TB
    subgraph aws["AWS"]
        subgraph services["Polyglot Microservices"]
            SVC1["Checkout Service (Java)"]
            SVC2["Notification Service (Go)"]
            SVC3["Recommendation Service (Python)"]
        end
        RP["GOFF Relay Proxy<br/>(lightweight container/sidecar)"]
        S3[("S3 Bucket<br/>flags.yaml")]
        PGR[("PostgreSQL retriever<br/>(alternative source)")]
    end
    CICD["CI/CD Pipeline"] -- "commits flags.yaml" --> S3

    SVC1 -- "OpenFeature Java SDK" --> RP
    SVC2 -- "OpenFeature Go SDK" --> RP
    SVC3 -- "OpenFeature Python SDK" --> RP
    RP -- "polls/retrieves" --> S3
    RP -. "optional alternative" .-> PGR
```

**Complexity:** Low — no mandatory database, a single lightweight binary/container, but no built-in admin UI for non-engineers.

---

### Use Case 1 — GitOps-managed flags-as-code via CI/CD

Flags are defined in a `flags.yaml` file committed to Git; CI/CD syncs it to S3 on merge; the relay proxy picks up changes automatically. Flag changes go through the same PR review as code.

```mermaid
sequenceDiagram
    participant Dev as Developer
    participant Git as Git Repo
    participant CI as CI/CD Pipeline
    participant S3 as S3 Bucket
    participant RP as GOFF Relay Proxy
    participant Svc as Checkout Service

    Dev->>Git: PR changes flags.yaml
    Git->>CI: merge triggers pipeline
    CI->>S3: upload updated flags.yaml
    loop periodic poll
        RP->>S3: fetch flags.yaml
        S3-->>RP: file (if changed)
    end
    Svc->>RP: evaluate("new-checkout-flow", ctx)
    RP-->>Svc: value (from last successfully loaded file)
    Note over RP,S3: If S3 is unreachable, relay proxy keeps<br/>serving the last successfully loaded flag set.
```

- **Complexity:** Low-Medium — requires CI/CD wiring; no admin UI means non-engineers can't self-serve toggles.
- **Dependencies:** S3 bucket, CI/CD pipeline, relay proxy.
- **Production impact if S3 is unavailable:** Low — in-memory fallback to last-loaded config; new changes simply can't land until connectivity is restored.

---

### Use Case 2 — Shared relay proxy for low-latency in-VPC evaluation across polyglot services

A shared ECS/EKS-hosted relay-proxy container serves flag evaluations to Java, Go, and Python services in the same cluster via OpenFeature (OFREP), avoiding a heavier platform purely to get one evaluation point across languages.

```mermaid
sequenceDiagram
    participant Checkout as Checkout Svc (Java)
    participant Notif as Notification Svc (Go)
    participant Reco as Recommendation Svc (Python)
    participant RP1 as Relay Proxy (task 1)
    participant RP2 as Relay Proxy (task 2)
    participant LB as Internal Load Balancer

    Checkout->>LB: evaluate flag (OFREP)
    Notif->>LB: evaluate flag (OFREP)
    Reco->>LB: evaluate flag (OFREP)
    LB->>RP1: route
    LB->>RP2: route
    Note over RP1,RP2: Multiple relay proxy tasks behind a load<br/>balancer avoid a single point of failure.
```

- **Complexity:** Low — single lightweight binary, no database required.
- **Dependencies:** relay proxy container(s), a retriever source (S3/Postgres/etc.), network path within the VPC.
- **Production impact if the relay proxy is down:** Medium if run as a single instance — unlike SDK-embedded local caching, services depend on the proxy being reachable at evaluation time. Mitigated by deploying multiple redundant proxy tasks, as shown above.

---

### Use Case 3 — PostgreSQL-retriever mode, reusing the existing RDS instance

Instead of S3, flags are stored in a dedicated table in the team's existing PostgreSQL/RDS instance that GOFF polls directly — "flags as data, queryable by SQL" rather than "flags as files."

```mermaid
sequenceDiagram
    participant Ops as Ops Engineer
    participant DB as PostgreSQL (existing RDS)
    participant GOFF as GOFF (embedded library mode)
    participant Svc as Inventory Service

    Ops->>DB: UPDATE flags table via SQL/admin tool
    loop periodic poll
        GOFF->>DB: SELECT flag rows
        DB-->>GOFF: rows
    end
    Svc->>GOFF: evaluate("low-stock-warning", ctx)
    GOFF-->>Svc: value (from local cache, refreshed by poll)
```

- **Complexity:** Low — reuses existing infrastructure, no new datastore technology.
- **Dependencies:** the PostgreSQL table, the GOFF binary (embedded or relay-proxy mode).
- **Production impact if Postgres is unavailable:** Low in embedded/library mode (in-memory cache refreshed periodically); Medium in relay-proxy mode without redundancy — same caveat as Use Case 2.

---

## In-House Facade (Alongside Unleash or GOFF)

Not a sixth tool — a thin custom layer wired in front of whichever engine won the standalone comparison (see `feature-flags-facade-sketch.md` for the API sketch). It inherits its host engine's runtime behavior entirely; what it adds is a typed API, auto-resolved context, centralized failure policy, and vendor-swap insurance.

### Runtime & Dependency Architecture

```mermaid
flowchart TB
    subgraph vpc["AWS VPC"]
        subgraph services["Spring Boot Microservices"]
            SVC1["Checkout Service"]
            SVC2["Billing Service"]
        end
        F1["FeatureFlags Facade"]
        F2["FeatureFlags Facade"]
        SPI["FlagEngine SPI"]
        ENGINE["UnleashFlagEngine OR GoffFlagEngine<br/>(config-selected adapter)"]
        BACKEND[("Unleash Server + PostgreSQL<br/>— or —<br/>GOFF Relay Proxy + S3/Postgres retriever")]
    end
    ENT[("Entitlements / Billing data<br/>(existing service)")]

    SVC1 --> F1
    SVC2 --> F2
    F1 --> SPI
    F2 --> SPI
    F1 -. "resolve tenant/plan traits" .-> ENT
    SPI --> ENGINE
    ENGINE --> BACKEND
```

**Complexity:** Same as whichever engine is selected (Low for GOFF, Medium for Unleash), plus one shared internal library every service takes a dependency on and one team that owns it.

---

### Use Case 1 — Tenant-aware targeting resolved from your own entitlements data

A rate-limit override should apply only to accounts on the Enterprise plan. Instead of re-declaring "Enterprise plan" as a segment inside the vendor UI, the facade resolves it from the entitlements service you already run.

```mermaid
sequenceDiagram
    participant Client
    participant Billing as Billing Service
    participant Facade as FeatureFlags Facade
    participant Ent as Entitlements Service (existing)
    participant Engine as FlagEngine (Unleash/GOFF)

    Client->>Billing: GET /usage-limits
    Billing->>Facade: isEnabled(RAISED_RATE_LIMIT)
    Facade->>Facade: FlagContext.current()
    Facade->>Ent: resolve tenant plan/traits
    Ent-->>Facade: plan = "enterprise"
    Facade->>Engine: evaluateBoolean(key, context)
    Engine-->>Facade: true/false (in-process, cached)
    Facade-->>Billing: result
    Billing-->>Client: 200 OK

    Note over Facade,Ent: Targeting trait resolved once from your<br/>own domain model — not duplicated as a<br/>vendor-side segment definition.
```

- **Complexity:** Low-Medium — wiring `FlagContext.current()` to the existing tenant/entitlements model.
- **Dependencies:** the underlying engine (Tier 1 performance either way) plus the entitlements service/data.
- **Production impact if the engine is unavailable:** identical to the host engine alone (negligible — cached, in-process). New dependency to design for: what the facade does if the *entitlements* lookup itself fails — this should have its own declared fallback trait, same discipline as the flag's own failure policy.

---

### Use Case 2 — Fleet-wide fail-open/fail-closed policy enforced consistently

A shipping-API kill switch needs identical failure behavior across every service that calls it — no team should be free to improvise whether it fails open or closed.

```mermaid
sequenceDiagram
    participant OnCall as On-call Engineer
    participant UI as Vendor Admin UI
    participant Engine as Unleash/GOFF backend
    participant FA as Facade (Order Svc)
    participant FB as Facade (Notification Svc)
    participant FC as Facade (Billing Svc)

    OnCall->>UI: toggle "shipping-api-kill-switch"
    UI->>Engine: update state
    par propagate
        Engine-->>FA: updated value (poll/stream)
        Engine-->>FB: updated value
        Engine-->>FC: updated value
    end
    Note over FA,FC: Every facade instance applies the SAME<br/>declared FailurePolicy for this flag — not<br/>whatever each team happened to code.

    Engine--xFB: simulated outage
    FB->>FB: catch exception, apply metadata().failurePolicy()
    Note over FB: Falls back to FAIL_OPEN automatically —<br/>identical to a healthy "false" response.
```

- **Complexity:** Low — this is a policy-consistency win, not new infrastructure.
- **Dependencies:** the engine, plus the `Feature` registry's declared `FailurePolicy` per flag (a governance artifact, not a runtime dependency).
- **Production impact if the engine is unavailable:** Bounded and predictable — every service reacts identically per the declared policy, closing the exact gap flagged in the FF4J and Flagsmith use cases above (inconsistent per-service handling).

---

### Use Case 3 — Swapping the underlying engine without touching call sites

GOFF was chosen for its light footprint, but six months in, the team decides Unleash's maturity is worth the heavier ops cost — or GOFF's project momentum stalls and they want an exit ramp.

```mermaid
sequenceDiagram
    participant Dev as Platform Engineer
    participant Cfg as Spring Config (flags.engine)
    participant Facade as FeatureFlags Facade
    participant SPI as FlagEngine SPI
    participant Old as GoffFlagEngine
    participant New as UnleashFlagEngine
    participant App as Checkout Service (unchanged)

    Note over App: App code only ever calls FeatureFlags —<br/>never imports Unleash or GOFF directly.

    Cfg->>SPI: flags.engine=goff (current)
    App->>Facade: isEnabled(NEW_CHECKOUT_FLOW)
    Facade->>SPI: evaluateBoolean(...)
    SPI->>Old: delegate
    Old-->>App: result (via Facade)

    Dev->>Cfg: flip flags.engine=unleash
    Cfg->>SPI: wire UnleashFlagEngine bean
    App->>Facade: isEnabled(NEW_CHECKOUT_FLOW)
    Facade->>SPI: evaluateBoolean(...)
    SPI->>New: delegate
    New-->>App: result (via Facade)

    Note over App: Zero code changes in Checkout Service<br/>across the entire engine migration.
```

- **Complexity:** Low for the migration itself (a config flip plus one adapter class); the Medium cost was paid up front building the facade that made this possible.
- **Dependencies:** both engines' SDKs available during the transition window; a deploy to flip config per service.
- **Production impact:** this use case *is* the mitigation for the vendor-risk row in the comparison doc (GOFF's smaller community, Unleash's heavier footprint) — the facade converts a potential rewrite into a config change.

---

## Cross-tool summary — production impact if the flag backend goes down

| Tool | Default failure mode | Mitigation | Fleet-level complexity |
|---|---|---|---|
| Unleash | SDK serves last cached config (poll-based, in-process) | None needed — caching is the default | Medium (server + Postgres to operate) |
| Flagsmith | **Remote-eval SDKs fail per-request by default** | Must explicitly enable local-evaluation mode | Medium (server + Postgres to operate) |
| GrowthBook | Bucketing SDK caches locally; stats engine is fully async/offline | None needed for the hot path | Medium-High (server + MongoDB + warehouse integration) |
| FF4J | Depends entirely on each service's own cache config — inconsistent by design across a fleet | Must standardize caching policy manually across every embedding service | Low per-service, rises at fleet scale (no central sync) |
| GO Feature Flag | Embedded/library mode caches like Unleash; relay-proxy mode depends on proxy availability | Deploy relay proxy with redundancy if that mode is used | Low (no mandatory database, single binary) |
| In-House Facade (alongside) | Identical to whichever engine it wraps — the facade adds no I/O of its own | Centralized `FailurePolicy` per flag, applied uniformly fleet-wide (see Use Case 2 above) | Same as the host engine, plus one shared internal library to own and version |

*This document is a discussion aid, not a final recommendation.*
