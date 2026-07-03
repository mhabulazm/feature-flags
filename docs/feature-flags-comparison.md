# Feature Flag Tooling — Shortlist Comparison

Context: Java / Spring Boot / PostgreSQL / AWS microservices. Five candidates: **Unleash**, **Flagsmith**, **GrowthBook**, **FF4J**, **GO Feature Flag (GOFF)**.

---

## Ranking — performance, setup complexity, scalability

Ordered top (best) to bottom (worst) using only these three criteria — this ignores targeting depth, experimentation features, and UI polish, which are covered in the tool sections below. Each criterion is scored 1–5 (5 = best); total out of 15.

Includes the in-house build option in two forms: **alone** (you build the evaluation engine, storage, and propagation yourself) and **alongside** (a thin custom layer on top of one of the shortlisted tools, which does the hard parts for you).

| Rank | Tool | Performance | Setup complexity | Scalability | Total |
|---|---|---|---|---|---|
| 1 (tie) | **Unleash** | 5 — always in-process, zero I/O per check | 3 — one new server (Node) + reuses Postgres | 5 — most proven at scale, has an Edge component for extreme horizontal scale | **13** |
| 1 (tie) | **GO Feature Flag** | 4 — in-process by default since provider v1.0.0 (2026); newer default | 5 — no mandatory database, single lightweight binary | 4 — stateless/lightweight scales easily, but smallest community of the group | **13** |
| 3 (tie) | **In-house, alongside Unleash** | 5 — inherits Unleash's always-in-process evaluation | 2 — Unleash's 3, minus a tax for the facade layer you now also maintain | 5 — inherits Unleash's proven scale | **12** |
| 3 (tie) | **In-house, alongside GOFF** | 4 — inherits GOFF's in-process-by-default evaluation | 4 — GOFF's 5, minus a tax for the facade layer | 4 — inherits GOFF | **12** |
| 3 (tie) | **In-house, alone (MVP scope)** | 5 — trivial to cache in-process at this narrow scope | 5 — a Postgres table + an in-process cache; days, not weeks | 2 — no admin UI, no cross-service propagation, no audit trail — doesn't hold as flags/teams multiply | **12** |
| 6 | **GrowthBook** | 5 — always in-process for flag/experiment evaluation | 2 — new server + new MongoDB + warehouse integration effort | 3 — flag eval scales trivially; the Mongo + warehouse pipeline needs tuning at volume | **10** |
| 7 | **FF4J** | 3 — capable of in-process performance, but plain JDBC store (the easy default) is not | 4 — no server at all, just a library, but fleet-wide coordination is manual | 2 — no central propagation; DB read amplification risk grows with service count | **9** |
| 8 (tie) | **Flagsmith** | 2 — default SDK mode is a live network call per check | 3 — one new server (Django) + reuses Postgres | 3 — fine once local-eval is configured; default mode bottlenecks under load | **8** |
| 8 (tie) | **In-house, alone (full-parity scope)** | 4 — achievable, but no guardrails against skipping caching — same trap as FF4J's default store | 1 — worst of every option; you're rebuilding what Unleash/Flagsmith already built | 3 — reachable, but propagation/HA/invalidation all have to be engineered and proven in-house | **8** |

**Unleash and GOFF tie at 13.** Tiebreak: Unleash ranks first because its scalability is proven in production at large scale, while GOFF's is architecturally sound but a younger, less battle-tested project — a scalability gap is harder to fix after the fact than a one-time setup cost, so proven scale wins the tiebreak over GOFF's lighter footprint. If your priority ordering weights setup effort over track record, swap them.

**The three-way tie at 12 is deceptive.** "Alongside Unleash/GOFF" earns its 12 honestly — you inherit a proven engine and pay only a small tax for a custom layer. "Alone (MVP scope)" earns the same 12 only because the scope is narrow; it's the version teams actually build first, and it rarely stays that narrow. As targeting, audit, multi-env, and cross-service propagation needs grow, it slides toward the full-parity row.

**In-house full-parity scope ties Flagsmith for last at 8 — but it's a worse 8.** Flagsmith's weak point (network call per check) is one config flag away from being fixed. In-house's weak point is open-ended engineering cost: this is the shape of the Wayfair example from the build-vs-buy research earlier — $3M+/year once a homegrown system reached this scope. And "alongside" never actually beats the paired tool's own score — its value (tenant-aware wrappers, org-specific UI, entitlement-system integration) isn't captured by these three criteria at all.

---

## 1. Unleash

**Description:** The most established open-source feature management platform. Runs as a standalone server (Node.js) with a web admin UI, backed by PostgreSQL. Server-side SDKs (including Java) poll the server and evaluate flags locally/in-process; a separate Edge/proxy component exists for client-side (browser/mobile) use.

**Pros**
- Native PostgreSQL backing store — no extra database technology to operate.
- Deepest flag-management feature set of the group: gradual rollout percentages, strategy constraints, flag dependencies, scheduling.
- Official, actively maintained Java SDK; large community, most GitHub stars in the space.
- Clear separation of admin plane (server) from evaluation plane (SDK), so flag checks don't add network latency per-request.
- **Performance:** always in-process evaluation — flag data is cached in memory and refreshed by a background poll, so a check is a local map lookup plus a hash function. Unleash's own docs describe this as "incredibly fast (nanoseconds)," with zero network or DB I/O on the request path.

**Cons**
- Another stateful service to deploy, patch, and keep highly available (on top of your own Postgres and microservices).
- Enterprise features (SSO/SAML, RBAC, change-request approvals, audit trail depth) are paywalled, not in the OSS core.
- Node.js runtime is a new operational dependency for an otherwise all-Java/AWS shop.

---

## 2. GO Feature Flag (GOFF)

**Description:** Open-source feature flag solution built from the ground up on the **OpenFeature** standard. Runs as either a lightweight self-hosted daemon (relay proxy) or embedded module. Flags are loaded from a "retriever" rather than a mandatory database — supports PostgreSQL, AWS S3, GCS, Git, Redis, MongoDB, Kubernetes ConfigMaps, and more.

**Pros**
- No database is strictly required — flags can live in an S3 bucket or your existing PostgreSQL via a retriever, minimizing new stateful infrastructure.
- Strong AWS fit: the S3 retriever plus flag-change export back to S3 suits an AWS-native shop well, and it runs as a single lightweight binary/container (low resource footprint).
- Built OpenFeature-first — you write application code against the vendor-neutral OpenFeature Java SDK from day one, making a future migration to another OpenFeature-compliant provider low-cost.
- MIT-licensed, no paid core/enterprise paywall split.
- **Performance:** as of provider v1.0.0 (2026), **in-process evaluation is the default** for the Java/OpenFeature provider — it polls the relay-proxy periodically, caches flag definitions locally with Caffeine, and evaluates in-process. Same nanosecond-scale, zero-per-request-I/O cost as Unleash, without needing extra configuration.

**Cons**
- Youngest and smallest-community project of the five — less battle-tested at scale, fewer enterprise-grade workflow features (approvals, complex scheduling).
- No polished built-in admin UI comparable to Unleash/Flagsmith/GrowthBook dashboards — flag files/retrievers are closer to "flags as config," which is GitOps-friendly but less approachable for non-engineers wanting self-service.
- Weaker built-in targeting/segmentation depth than Unleash or Flagsmith.
- Because there's no central mandatory datastore, you're responsible for designing your own retrieval/caching/refresh strategy per environment.

---

## 3. GrowthBook

**Description:** Open-source feature flagging platform built around experimentation. Standalone app (Node.js) for flag/experiment management; a built-in stats engine (Bayesian and Frequentist, CUPED support) computes experiment results by querying *your own* data warehouse rather than owning the analytics data itself.

**Pros**
- Best-in-class experimentation/A/B-testing statistics of the group — a real differentiator if flags will double as an experimentation platform.
- Visual (no-code) experiment editor for product/growth users, unusual among self-hosted OSS tools.
- MIT-licensed core; generous free self-hosted tier (unlimited flags/users/experiments).
- Since it reads experiment results directly from your warehouse, it can point at your existing PostgreSQL for analytics data.
- **Performance:** same in-process model as Unleash — the SDK caches feature/experiment definitions locally and buckets users via consistent hashing, so flag/experiment evaluation costs the same negligible in-process time. The stats engine runs entirely off the request path against the warehouse.

**Cons**
- The application itself (flag/experiment metadata, not analytics data) requires **MongoDB** — a new database technology alongside your PostgreSQL, unlike Unleash/Flagsmith.
- Flag-management maturity (approval workflows, complex targeting, dependencies) is weaker than Unleash's.
- Most value is unlocked only if you actually run experiments against a connected warehouse — as a pure on/off flag system it's a heavier setup than the alternatives.
- Enterprise features again gated behind a paid tier.

---

## 4. FF4J (Feature Flipping for Java)

**Description:** A pure Java library, not a separate server — you embed it directly in your Spring Boot application. Feature state is persisted via a pluggable `FeatureStore` interface with 20+ backend implementations, including JDBC/PostgreSQL. Ships an optional web console and REST API you can host yourself.

**Pros**
- No new service to run by default — it's a library dependency plus your existing PostgreSQL, minimizing new infrastructure.
- Broadest database backend support of any option here (JDBC/Postgres, Redis, MongoDB, DynamoDB, and more), so it fits whatever storage you already operate.
- Spring Boot starter available; rich strategy support (time-based, percentage-based, custom flip strategies) and built-in audit/property management.
- Being Java-native, integration is direct — no cross-language client/server protocol.

**Cons**
- No central multi-service admin UI unless you deploy the optional console yourself and wire every microservice to a shared store — in a microservices fleet this reintroduces the "keep everyone in sync" coordination problem that dedicated servers (Unleash/Flagsmith) solve out of the box.
- Smaller community and slower release cadence than Unleash/Flagsmith/GrowthBook — more risk of stalled maintenance.
- No built-in cross-service change propagation/streaming; each instance needs its own polling/caching strategy against the shared store.
- Lacks the polished non-engineer-facing UI and workflow features (approvals, environments-as-first-class-concept) of the standalone platforms.
- **Performance:** fully configuration-dependent, and nothing forces the safe choice. A plain `JdbcFeatureStore` hits Postgres on every check — FF4J's own docs warn this can "put pressure on the DB" under high check volume, and that load multiplies across every service sharing the table. An in-memory store, or wrapping the store with `FF4jCacheProxy` (EhCache/Redis/Hazelcast/JCache), gets the same near-zero in-process cost as Unleash — but you have to opt in explicitly.

---

## 5. Flagsmith

**Description:** Open-source feature flag and remote-config platform. Standalone server (Python/Django) backed by PostgreSQL, with a management dashboard, REST API, and SDKs for 15+ languages including Java. Goes beyond simple booleans into remote config values and identity/user-based targeting.

**Pros**
- Native PostgreSQL backing store, same as Unleash.
- Combines feature flags *and* remote configuration (key/value config per environment) in one tool, reducing the need for a separate config-management system.
- Strong identity-based targeting model (target by user/segment, not just percentage buckets).
- Core (flags, targeting, multivariate flags) is BSD-3-Clause — permissive and self-host-friendly.
- Official Java client.

**Cons**
- Enterprise Edition features (RBAC, SSO/SAML, some integrations) are closed-source, same trade-off as Unleash.
- Server-side SDKs call the API per-evaluation by default; local/in-process evaluation requires explicitly enabling a caching mode, otherwise it adds a network hop to every flag check.
- Python/Django server is, like Unleash's Node server, a runtime stack outside your existing Java/AWS footprint.
- Flag-management depth (complex scheduling, dependencies) is less mature than Unleash's.
- **Performance:** the **default** server-side SDK mode makes a live HTTP call to the Flagsmith server on every flag check — real request-path latency (typically single- to double-digit milliseconds, worse under Flagsmith server load) and a partial availability dependency on Flagsmith itself. Enabling local evaluation mode caches the environment doc and gets evaluation down to the same in-process, near-zero cost as Unleash — but it's opt-in, not the default.

---

## Performance cost on the request path

Flag checks happen on the hot path of every request that uses them, so how each tool evaluates a flag matters as much as its feature set. The five split cleanly into two tiers.

### Tier 1 — in-process evaluation, no network/DB call per check

Flag rules and current state are cached in application memory (refreshed by an infrequent background poll, decoupled from request traffic) and evaluated as a pure in-process function — a local map lookup plus a hash function, typically low-nanosecond to low-microsecond cost.

- **Unleash** — this is the only mode the server-side Java SDK has; always in-process.
- **GrowthBook** — same model: definitions cached locally, users bucketed via consistent hashing in-process.
- **GO Feature Flag** — in-process evaluation became the **default** for the Java/OpenFeature provider as of v1.0.0 (2026); it polls the relay-proxy and caches locally with Caffeine. (Pin a recent provider version — earlier versions defaulted to remote mode.)
- **FF4J** — reaches this tier *only if* you use an in-memory store, or explicitly wrap a store with `FF4jCacheProxy`. Not automatic.

### Tier 2 — live network or DB call per check, unless reconfigured

- **Flagsmith** — the server-side SDK's **default** mode is a live HTTP call to the Flagsmith server per flag check (real request-path latency, typically single- to double-digit milliseconds under healthy conditions, plus a partial availability coupling to Flagsmith). Local evaluation mode moves it to Tier 1, but it's opt-in.
- **FF4J** — with a plain `JdbcFeatureStore` and no caching decorator, every check is a JDBC round trip to Postgres. Because there's no central cache tier, this load multiplies across every service sharing the table — a real scaling risk that's specific to FF4J's embedded-by-default design.

### Practical takeaway

For a genuinely high-throughput, latency-sensitive path (pricing, checkout, anything evaluated per-request at volume), **Unleash**, **GrowthBook**, and **GOFF running a recent in-process provider** all give the same nanosecond-scale, zero-I/O-per-check behavior with no special tuning required. **Flagsmith** and **FF4J** can reach that same tier, but only with deliberate configuration (local-eval mode; caching decorator) — left at defaults, both silently add a live network or DB call to every single flag check, which is an easy trap to fall into and only shows up as a problem once you're under real load.

---

## Side-by-side summary

| | Unleash | GO Feature Flag | GrowthBook | FF4J | Flagsmith |
|---|---|---|---|---|---|
| **Rank (perf + setup + scalability, incl. in-house variants)** | 1 (tie) | 1 (tie) | 6 | 7 | 8 (tie) |
| **License (core)** | Apache-2.0 | MIT | MIT | Apache-2.0 | BSD-3-Clause |
| **Runs as** | Standalone server | Daemon or embedded | Standalone server | Embedded Java library | Standalone server |
| **Required datastore** | PostgreSQL | None mandatory — retriever-based (Postgres, S3, etc.) | MongoDB (app) + your warehouse | Your choice (JDBC/Postgres, etc.) | PostgreSQL |
| **New infra beyond Postgres/AWS** | Node.js server | None (or a small Go binary) | Node.js server + MongoDB | None | Python/Django server |
| **Java support** | Official SDK | OpenFeature provider | Official SDK | Native (it's Java) | Official SDK |
| **Rollout/targeting depth** | Deepest | Moderate | Moderate | Strong (via strategies) | Strong (+ remote config) |
| **Experimentation/stats** | No | No | Best-in-class | No | No |
| **Central admin UI** | Yes | No polished UI | Yes (incl. visual editor) | Optional, self-hosted | Yes |
| **Request-path perf (default config)** | Negligible — in-process (Tier 1) | Negligible — in-process (Tier 1, provider v1.0+) | Negligible — in-process (Tier 1) | DB round-trip per check (Tier 2) unless caching configured | Live network call per check (Tier 2) unless local-eval enabled |
| **Biggest strength** | Maturity + rollout depth | AWS/S3-native, OpenFeature-first | Experimentation engine | Zero new infra, embedded | Flags + remote config combined |
| **Biggest risk** | Extra service to run | Smaller community, thinner UI | Adds MongoDB dependency | No cross-service sync out of the box | Extra service to run |

---

## Final shortlist — 4 winners

The performance/setup/scalability scoring converges on four options tied at the top (13, 13, 12, 12) — two standalone tools and a thin in-house layer on each of them:

| Winner | Type | Distinguishing trade-off |
|---|---|---|
| **Unleash** | Standalone tool | Most mature/proven at scale; heavier ops footprint (a Node.js server to run alongside Postgres) |
| **GO Feature Flag** | Standalone tool | Lightest ops footprint of the four (no mandatory database, single binary); youngest, smallest community |
| **In-house, alongside Unleash** | Custom layer + Unleash engine | Unleash's maturity, plus org-specific ergonomics (typed API, tenant-aware context, governance) — at the cost of a facade you now own and maintain |
| **In-house, alongside GOFF** | Custom layer + GOFF engine | GOFF's light footprint, plus the same customization benefit — same facade-maintenance cost |

Within this group of four, the performance/setup/scalability axis no longer discriminates — they're statistically tied. The decision collapses to two independent questions:

1. **Unleash vs. GOFF (maturity vs. footprint):** proven-at-scale, richer UI, heavier server vs. newest project, minimal ops footprint, thinner UI.
2. **Standalone vs. alongside (build a facade or not):** worth it only if you have real recurring needs the vendor UI/SDK doesn't serve well out of the box — tenant-aware targeting resolved from your own entitlements data, org-wide fallback policy, governance (stale-flag detection, naming/ticket conventions), or unified internal observability. If none of that applies yet, the standalone tool alone is strictly less to build and maintain.

If the "alongside" option looks like the right fit, see `feature-flags-facade-sketch.md` for a concrete API sketch (typed flag registry, engine-swappable SPI, centralized failure policy) and the guardrails that keep it from sliding into the "alone, full-parity" row that scores worst.

---

*This document is a starting point for discussion — not a final recommendation.*
