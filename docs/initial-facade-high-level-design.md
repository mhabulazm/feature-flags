# Feature Flags Facade — High-Level Design

## Purpose

A thin in-house library that sits between application code and a third-party feature-flag engine (Unleash or GO Feature Flag). It provides a typed, vendor-agnostic API, centralized failure-policy enforcement, auto-resolved evaluation context, and engine-swap insurance — without duplicating the engine's storage, rollout math, or admin plane.

---

## Design Principles

1. **Consume, never own.** The facade reads engine state but never stores, computes, or administers an independent copy of it.
2. **Engine-agnostic at compile time.** No module an application service imports for day-to-day flag checks may declare a dependency on Unleash's or GOFF's SDK. Vendor dependencies live only in adapter modules.
3. **Decentralized flag ownership.** Each service defines its own flag registry — there is no single, org-wide flag enum. This prevents cross-team merge-contention and avoids fleet-wide redeploys to add a flag.
4. **Additive evolution.** The public interfaces evolve through default methods, not signature changes. A breaking change has fleet-wide blast radius and must be a deliberate MAJOR version.

---

## Architecture

```
┌────────────────────────────────────────────────┐
│              Application Services               │
│  (Checkout, Billing, Inventory, ...)           │
│                                                 │
│  Calls only: FeatureFlags.isEnabled(key)        │
│  Never imports: Unleash SDK, GOFF SDK           │
└────────────────────┬───────────────────────────┘
                     │
                     ▼
┌────────────────────────────────────────────────┐
│               FeatureFlags Facade               │
│                                                 │
│  ┌──────────────┐  ┌─────────────────────────┐ │
│  │  Public API   │  │  Auto-injected Context  │ │
│  │  FeatureFlags │  │  FlagContext.current()   │ │
│  └──────┬───────┘  │  (tenant, user, traits)  │ │
│         │           └────────────┬────────────┘ │
│         ▼                        │              │
│  ┌──────────────────────────────────────────┐  │
│  │         DefaultFeatureFlags               │  │
│  │  Centralized failure-policy enforcement   │  │
│  │  Metrics emission (Micrometer)            │  │
│  └──────────────────┬───────────────────────┘  │
│                     │                           │
│                     ▼                           │
│  ┌──────────────────────────────────────────┐  │
│  │           FlagEngine SPI                  │  │
│  │  evaluateBoolean(key, context, default)   │  │
│  │  evaluateVariant(key, context, type, def) │  │
│  └──────────┬───────────────┬───────────────┘  │
│             │               │                   │
│             ▼               ▼                   │
│  ┌──────────────┐  ┌──────────────────────┐    │
│  │ Unleash      │  │ GOFF                 │    │
│  │ FlagEngine   │  │ FlagEngine           │    │
│  │ adapter      │  │ adapter              │    │
│  └──────┬───────┘  └──────────┬───────────┘    │
└─────────┼──────────────────────┼────────────────┘
          │                      │
          ▼                      ▼
┌──────────────────┐  ┌──────────────────────┐
│  Unleash Server  │  │  GOFF Relay Proxy    │
│  + PostgreSQL    │  │  + S3 / PostgreSQL   │
└──────────────────┘  └──────────────────────┘
```

**Key property:** App code → Facade → SPI → Engine is the entire call chain. The engine evaluates from its in-process cache (background-polled). The facade adds no I/O on the hot path — it's a pass-through with policy enforcement and metrics.

---

## Module Layout

| Module | Purpose | Ships |
|---|---|---|
| `flags-api` | Public contracts — `FeatureFlags`, `FlagKey`, `FlagContext`, `FlagMetadata`, `ConfigKey` | Now |
| `flags-spi` | `FlagEngine` interface — the swappable adapter contract | Now |
| `flags-noop` | `NoOpFlagEngine` (always returns default) and `InMemoryFlagEngine` (config-driven overrides, e.g. AWS Parameter Store) | Now |
| `flags-test-fixtures` | `FakeFeatureFlags` — test double for consumers of the facade | Now |
| `flags-contract-test` | Abstract test suite every `FlagEngine` implementation must pass | Now |
| `flags-spring-boot-starter` | Auto-configuration — wires the active engine via `flags.engine=<name>` property | Now |
| `flags-engine-unleash` | `UnleashFlagEngine` adapter — the only module importing Unleash's SDK | After ADR 0002 |
| `flags-engine-goff` | `GoffFlagEngine` adapter — the only module importing GOFF's SDK | After ADR 0002 |

The first six modules are zero vendor-dependency and can be built, tested, and consumed by services immediately. The last two are thin adapters wired in later via a config flip — same swap mechanic as engine-A ↔ engine-B, exercised in the direction no-engine → an-engine.

---

## Core Concepts

### FlagKey — Typed flag identifiers

Each service defines its own enum implementing `FlagKey`. A typo in a flag name becomes a compile error, not a silent no-op. The enum carries governance metadata:

- **`key()`** — the string used in the engine backend, namespaced by service (`checkout.new-checkout-flow`)
- **`owner`** — owning team
- **`ticket`** — linked issue/feature reference
- **`defaultValue`** — what is returned when the engine is unreachable
- **`failureSemantics`** — `FAIL_OPEN` or `FAIL_CLOSED` (human-readable label validated against `defaultValue`)
- **`expiresAfter`** — when this flag should be removed (null = permanent config, not a rollout flag)

### Degenerate registries, not a global enum

A single org-wide enum would become a cross-team merge hotspot and force fleet-wide redeploys to add one team's flag. Instead, each service owns its registry. The engine's own admin UI (Unleash dashboard or GOFF flag source) is the org-wide source of truth for live state. The per-service enums are typed handles into that backend — not a competing catalog.

### FlagContext — Auto-resolved evaluation context

`FlagContext.current()` resolves tenant, user, and plan traits from the existing request-scoped security/tenant context. Most call sites never build a context by hand. Traits are pulled from the entitlements data already available, rather than being re-declared as segments in the vendor UI.

**Resilience contract (per ADR 0002):** By default, context resolution reads from in-memory, request-scoped data — no I/O. If a live call to an entitlements service is unavoidable for some flags, it must carry timeout + circuit breaker + declared fallback trait; a bare `try/catch` is insufficient for a synchronous service dependency on the evaluation hot path.

### FeatureFlags — The public API

Three abstract methods; everything else is a default method built on them (allowing additive evolution without breaking existing adapters):

- `isEnabled(FlagKey, FlagContext)` → boolean
- `getVariant(FlagKey, Class<T>, T defaultValue, FlagContext)` → T
- `getConfigValue(ConfigKey<T>, FlagContext)` → T

No-context overloads default to `FlagContext.current()`. No string flag names. No vendor SDK import.

### ConfigKey — Typed remote configuration

Extends `FlagKey` for typed remote-config values (strings, numbers, JSON). v1 scope: implemented as a thin wrapper over `evaluateVariant` returning a typed payload. Full remote-config semantics (Flagsmith-style key/value store) are explicitly deferred — neither Unleash nor GOFF needs it as a first-class concept.

### FlagEngine SPI — The swap point

Two methods, minimal surface:

- `evaluateBoolean(key, context, defaultValue)` → boolean
- `evaluateVariant(key, context, type, defaultValue)` → T

**Contract** (enforced by shared test suite):
- Unknown key → return `defaultValue`, never throw
- Transport failure → may throw (facade catches and applies `FlagMetadata.defaultValue()`)
- Null/empty context → treated as "no targeting available," not an error
- Thread-safe for concurrent calls

---

## Key Flows

### Happy-path evaluation

```
App: isEnabled(CheckoutFlags.NEW_CHECKOUT_FLOW)
  → Facade: resolve FlagContext.current() (in-memory, no I/O)
  → Facade: delegate to FlagEngine.evaluateBoolean(key, context, default)
  → Engine: in-process cache lookup (nanosecond, no network/DB call)
  → Facade: emit counter metric (flag, result)
  → return result to app
```

### Engine-unreachable fallback

```
App: isEnabled(CheckoutFlags.NEW_CHECKOUT_FLOW)
  → Facade: delegate to FlagEngine → ENGINE THROWS
  → Facade: catch exception, log warning
  → Facade: return FlagMetadata.defaultValue()  (FAIL_CLOSED → false for this flag)
  → same behavior, every service, per the flag's declared policy
```

This is enforced centrally in `DefaultFeatureFlags` — no team decides ad hoc what happens when the engine is down.

### Engine swap

```
flags.engine=goff  →   flags.engine=unleash
                              │
App code: unchanged ──────────┘
```

Zero call-site changes across the entire fleet. The SPI makes the vendor an implementation detail.

---

## Rollout Strategy (doesn't wait on engine choice)

| Phase | What | When |
|---|---|---|
| **Phase 1** | Build `flags-api`, `flags-noop`, `flags-test-fixtures`, `flags-contract-test`, `flags-spring-boot-starter`. Publish as `0.x`. | Now |
| **Phase 2** | Services integrate against `FeatureFlags` + their own registries, wired to `NoOpFlagEngine` (static defaults) or `InMemoryFlagEngine` (config-driven overrides via AWS Parameter Store). | Now |
| **Phase 3** | ADR 0002 ratifies the engine. Build the corresponding `flags-engine-*` adapter. Pass contract test. Cut `1.0.0`. | After ratification |
| **Phase 4** | Flip `flags.engine=<chosen>` per service. Zero call-site changes. | After Phase 3 |

The typed API, governance metadata, and consistent failure behavior land in every service in Phase 2. The engine decision becomes a config change layered on top — the same mechanic as engine A → engine B, exercised in the direction no-engine → an-engine.

---

## Guardrails — Staying "Alongside", Not Sliding into "Alone"

**Core rule:** The facade may consume the engine's state but must never become a second owner of it.

**Permanently out of scope for the facade:**
- Independent flag storage (a table that's an alternate source of truth)
- Independent rollout/bucketing math (percentage rollouts, consistent hashing)
- A second admin/control-plane UI
- Approval workflows, change-request review, or governance features the engine's Enterprise tier handles

**The test for where the line is:** If the facade vanished tomorrow, would the engine's own state still be fully correct and complete? If yes, it's read-only consumption. If no (the engine's state would be incomplete without consulting the facade), that's the tripwire.

**How creep happens** (the four paths to watch):
1. **Storage creep** — a local cache table that ops can edit directly during incidents, creating two sources of truth with no defined conflict-resolution rule
2. **Rollout-logic creep** — homegrown bucketing math for "just this one flag" that the vendor targeting can't express
3. **Control-plane creep** — an internal admin page duplicating the vendor UI
4. **Workflow creep** — rebuilding approval state machines that are a paywalled Enterprise feature

**Practical guardrails:**
- `FlagEngine` stays narrow — evaluate methods plus nothing else. A PR that adds a write path is a signal to stop.
- Treat "we need X and the OSS tool doesn't have it" as a fork: read-only tooling (governance, metrics) stays in the facade; control-plane features mean paying for the vendor's Enterprise tier or accepting the gap.
- Any cache in the facade mirrors what the SDK already does internally (poll-refreshed, read-through) — never an independently-writable store ops can edit directly.

---

## Governance Automation (per ADR 0002)

Two deliverables in the first release, driven by evidence that manual cleanup discipline consistently fails:

1. **Stale-flag detection job** — scheduled job that surfaces removal-lag ratio, inventory-growth rate, and lifespan percentiles. Observes and notifies; never auto-removes a flag.
2. **CI gate on registry entries** — `owner`, `ticket`, and `expiresAfter` (or explicit `permanent` marker) enforced mechanically, not by reviewer memory.

Both are read-only over registries and engine state, keeping them inside the core guardrail.

---

## Testing Strategy

| Layer | Approach |
|---|---|
| **Facade unit tests** | `DefaultFeatureFlags` tested against a `MockFlagEngine` (configurable — throws on demand, returns fixed values). Separate from `FakeFeatureFlags`. |
| **SPI contract tests** | Abstract `FlagEngineContractTest` base class (`unknownKeyReturnsDefault`, `nullContextTreatedAsEmpty`, `concurrentEvaluationIsSafe`). Every engine implementation extends it — holding all adapters to the same bar. |
| **Consumer tests** | `FakeFeatureFlags` — a simple `Map<FlagKey, Object>`-backed double. No vendor SDK mocking required in any service test. |
| **Integration tests** | Deferred until engine is chosen (Testcontainers running Unleash + Postgres, or GOFF relay-proxy container). Safe to defer because the contract test suite already pins adapter correctness. |

---

## CI Gates

1. **`mvn verify`** — compile, unit tests, contract tests against every `FlagEngine` on classpath
2. **ArchUnit** — fails the build if `flags-api` or `flags-spi` imports anything from an Unleash or GOFF package
3. **Binary compatibility gate** (`japicmp` / `revapi`) — fails the build on accidental breaking changes to `FeatureFlags` or `FlagEngine`
4. **Registry CI gate** — fails if any `FlagKey` entry is missing `owner`, `ticket`, or `expiresAfter`/`permanent` marker

---

## Versioning

| Change | Bump |
|---|---|
| New default method on `FeatureFlags` | MINOR |
| Change to abstract signature on `FeatureFlags` or `FlagEngine` | MAJOR — avoid |
| New non-default method on `FlagEngine` | MAJOR — prefer a default |
| Service adds a `FlagKey` to its own registry | n/a — service-owned |
| Bug fix, no interface change | PATCH |

Pre-1.0 (`0.x`): interfaces may shift based on what an adapter reveals. Cut `1.0.0` once the first real engine adapter passes contract tests.

---

## Tech Stack

| Concern | Choice |
|---|---|
| Build | Maven multi-module reactor + `flags-bom` |
| CI/CD | GitHub Actions |
| Artifacts | Self-hosted Nexus |
| JDK | Java 21 (LTS) |
| Test | JUnit 5 (Jupiter) + AssertJ |
| Metrics | Micrometer (CloudWatch registry) |
| Logging | SLF4J API only — consumer supplies binding |

---

## References

- `adr/0001-adopt-in-house-facade-alongside-flag-engine.md` — decision to adopt the alongside pattern
- `adr/0002-select-flag-engine-and-evolve-facade-operating-model.md` — engine recommendation (GOFF) and operating-model amendments
- `docs/feature-flags-facade-design.md` — finalized interface contracts, module layout, versioning, testing
- `docs/feature-flags-facade-sketch.md` — narrative walkthrough of the facade pattern
- `docs/feature-flags-comparison.md` — tool comparison and scoring
- `docs/feature-flags-use-cases.md` — per-tool runtime diagrams and failure-mode analysis
