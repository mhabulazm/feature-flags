# In-House Facade — Design (Diagram-Forward Draft)

Draft companion to `feature-flags-facade-design.md` — same design, expressed as diagrams. Full prose, versioning rationale, and contract wording live there. ADR 0002's proposed evolutions (governance automation, interaction scan, resilience contract) are **not** folded in here yet. Package placeholder: `com.acme.flags`.

---

## Principles

1. Consume engine state — never own a second copy.
2. Engine-agnostic at compile time — vendor SDKs live only in adapter modules.
3. Decentralized registries — no org-wide enum.
4. Additive evolution — a breaking public-API change is fleet-wide.

---

## Module layout

```mermaid
flowchart TB
    subgraph now["Ships now — zero vendor deps"]
        API["flags-api<br/>FeatureFlags · FlagKey · FlagContext<br/>FlagMetadata · ConfigKey · FlagEngine SPI"]
        NOOP["flags-noop<br/>NoOpFlagEngine · InMemoryFlagEngine"]
        FIX["flags-test-fixtures<br/>FakeFeatureFlags"]
        CT["flags-contract-test<br/>FlagEngineContractTest"]
        START["flags-spring-boot-starter<br/>auto-config; engine wired by property"]
    end
    subgraph later["Ships after ADR 0002 — vendor deps isolated here"]
        UNL["flags-engine-unleash<br/>Unleash SDK"]
        GOFF["flags-engine-goff<br/>OpenFeature SDK + GOFF provider"]
    end

    NOOP --> API
    FIX --> API
    CT --> API
    START --> API
    UNL --> API
    GOFF --> API
    START -.->|"one engine on classpath"| NOOP
    START -.->|"later"| UNL
    START -.-> GOFF

    classDef nowcls fill:#e6f7ff,stroke:#0958d9;
    classDef latercls fill:#fff7e6,stroke:#d46b08,stroke-dasharray:4 4;
    class API,NOOP,FIX,CT,START nowcls;
    class UNL,GOFF latercls;
```

*All modules depend only on `flags-api`; vendor SDKs never reach it. An ArchUnit test fails the build if `flags-api`/`flags-spi` imports an Unleash or GOFF package.*

*Update: `flags-engine-goff`'s module shell already shipped on `main` as an inert pre-ratification skeleton — see `feature-flags-facade-design.md`'s module-layout section for the detail. "Ships after ADR 0002" here means the functioning adapter, not the module's existence.*

---

## Core interfaces

```mermaid
classDiagram
    class FeatureFlags {
        <<interface>>
        +isEnabled(FlagKey, FlagContext) boolean
        +getVariant(FlagKey, Class, T, FlagContext) T
        +getConfigValue(ConfigKey, FlagContext) T
    }
    note for FeatureFlags "3 no-context overloads are default methods → FlagContext.current()"
    class FlagEngine {
        <<interface>>
        +evaluateBoolean(String, Map, boolean) boolean
        +evaluateVariant(String, Map, Class, T) T
    }
    note for FlagEngine "SPI · unknown key returns default · throws only on transport failure · thread-safe"
    class FlagKey {
        <<interface>>
        +key() String
        +metadata() FlagMetadata
    }
    class ConfigKey {
        <<interface>>
        +type() Class
        +defaultValue() T
    }
    class FlagMetadata {
        <<record>>
        +owner String
        +ticket String
        +defaultValue boolean
        +failureSemantics FailureSemantics
        +expiresAfter Duration
    }
    class FailureSemantics {
        <<enumeration>>
        FAIL_OPEN
        FAIL_CLOSED
    }
    class FlagContext {
        +current() FlagContext
        +forTenant(String) FlagContext
        +anonymous() FlagContext
        +toMap() Map
    }
    note for FlagContext "static factories; current() resolves request-scoped tenant/security context"
    class DefaultFeatureFlags {
        -FlagEngine engine
        -MeterRegistry metrics
    }
    class NoOpFlagEngine
    class InMemoryFlagEngine
    class FakeFeatureFlags

    FeatureFlags <|.. DefaultFeatureFlags
    FeatureFlags <|.. FakeFeatureFlags
    FlagEngine <|.. NoOpFlagEngine
    FlagEngine <|.. InMemoryFlagEngine
    DefaultFeatureFlags --> FlagEngine : delegates
    FlagKey <|-- ConfigKey
    FlagKey --> FlagMetadata
    FlagMetadata --> FailureSemantics
```

*`defaultValue` (what is returned on failure) is split from `failureSemantics` (the human-readable label lint validates against it). `FeatureFlags` has only 3 abstract methods; everything else is a default method — that keeps additive evolution a MINOR bump.*

---

## Registries — per-service, not a global enum

```mermaid
flowchart TB
    API["flags-api<br/>FlagKey (shared interface)"]
    subgraph checkout["checkout-service · own codebase"]
        CF["enum CheckoutFlags implements FlagKey<br/>key: checkout.new-checkout-flow"]
    end
    subgraph billing["billing-service · own codebase"]
        BF["enum BillingFlags implements FlagKey<br/>key: billing.rate-limit-override"]
    end
    UI[("Engine admin UI — Unleash / GOFF<br/>org-wide source of truth for live state")]

    CF -->|implements| API
    BF -->|implements| API
    CF -.->|"namespaced keys"| UI
    BF -.->|"namespaced keys"| UI
```

*Each service owns its registry (no cross-team merge hotspot, no fleet redeploy to add a flag). The enums are typed handles into the engine's own catalog — not a competing catalog.*

---

## Evaluation + centralized failure policy

```mermaid
sequenceDiagram
    participant App as Application code
    participant FF as DefaultFeatureFlags
    participant Ctx as FlagContext
    participant Eng as FlagEngine (in-process cache)
    participant M as Micrometer

    App->>FF: isEnabled(CheckoutFlags.NEW_CHECKOUT_FLOW)
    FF->>Ctx: current()
    Ctx-->>FF: tenant / user / traits
    alt engine reachable
        FF->>Eng: evaluateBoolean(key, ctx, metadata.defaultValue)
        Eng-->>FF: true / false
    else engine throws (transport failure)
        FF->>FF: catch → metadata.defaultValue (FAIL_OPEN / FAIL_CLOSED)
    end
    FF->>M: counter("feature_flag.evaluated").increment()
    FF-->>App: result
```

*The engine evaluates from its in-process cache — no network hop on the hot path. Fallback-to-default lives in one place, applied identically fleet-wide, per each flag's declared semantics.*

---

## Testing — one contract, every engine

```mermaid
classDiagram
    class FlagEngineContractTest {
        <<abstract>>
        #engine() FlagEngine
        +unknownKeyReturnsDefault()
        +nullContextTreatedAsEmpty()
        +concurrentEvaluationIsSafe()
    }
    note for FlagEngineContractTest "engine() is abstract — supplied by each subclass"
    class NoOpFlagEngineTest
    class InMemoryFlagEngineTest
    class UnleashFlagEngineTest
    class GoffFlagEngineTest

    FlagEngineContractTest <|-- NoOpFlagEngineTest
    FlagEngineContractTest <|-- InMemoryFlagEngineTest
    FlagEngineContractTest <|-- UnleashFlagEngineTest
    FlagEngineContractTest <|-- GoffFlagEngineTest
```

*Ship-now engines (NoOp, InMemory) pass it today; vendor adapters extend the same base after ADR 0002 — holding every engine to one bar without `flags-api` touching any SDK. `FakeFeatureFlags` is the separate double for facade **consumers**.*

---

## Rollout — doesn't wait on the engine choice

```mermaid
flowchart LR
    P1["Phase 1 · now<br/>build the 5 zero-dep modules<br/>publish 0.x"]
    P2["Phase 2<br/>services integrate vs<br/>NoOp / InMemory<br/>(static rollout via AWS Param Store)"]
    P3["Phase 3 · ADR 0002<br/>build flags-engine-*<br/>pass contract test → cut 1.0.0"]
    P4["Phase 4<br/>set flags.engine per service<br/>zero call-site changes"]

    P1 --> P2 --> P3 --> P4
```

*Typed API, governance metadata, and consistent failure behavior land in every service now; the engine decision becomes a config change layered on top — the same swap mechanic as engine-A→engine-B, run in the direction no-engine→an-engine.*

---

## CI gates

```mermaid
flowchart TB
    PR["Pull request"] --> V["mvn verify<br/>unit + contract tests<br/>(every engine on classpath)"]
    V --> A["ArchUnit<br/>fail if flags-api/spi imports Unleash/GOFF"]
    A --> C["japicmp / revapi<br/>fail on breaking API change (SemVer gate)"]
    C --> MG{"merge to main?"}
    MG -->|yes| D["mvn deploy → Nexus SNAPSHOT"]
    D --> T["tag → versioned release<br/>0.x until first adapter passes its contract test"]
```

---

## Versioning (blast radius: every service)

| Change | Bump |
|---|---|
| New default method on `FeatureFlags` | MINOR |
| Change an abstract signature on `FeatureFlags` / `FlagEngine` | MAJOR — avoid |
| New non-default method on `FlagEngine` | MAJOR — prefer a default instead |
| Service adds a `FlagKey` to its own registry | n/a — service-owned |

---

## Tech stack

| Concern | Choice |
|---|---|
| Build | Maven multi-module reactor + `flags-bom` |
| CI/CD | GitHub Actions |
| Artifacts | Self-hosted Nexus |
| JDK | Java 21 (LTS) |
| Test | JUnit 5 (Jupiter) + AssertJ |
| Metrics | Micrometer (CloudWatch registry) |
| Logging | SLF4J API only — consumer supplies the binding |

---

## References

- `feature-flags-facade-design.md` — canonical prose spec this mirrors
- `../adr/0001-adopt-in-house-facade-alongside-flag-engine.md`, `../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md`
- `feature-flags-facade-sketch.md`, `feature-flags-comparison.md`, `feature-flags-use-cases.md`
