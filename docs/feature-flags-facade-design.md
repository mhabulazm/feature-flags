# In-House Facade — Finalized Design

Formalizes `feature-flags-facade-sketch.md` (a narrative walkthrough) into a buildable contract: interfaces, module boundaries, versioning rules, and a testing strategy. This is the spec to implement from. Engine selection (Unleash vs. GO Feature Flag, see ADR 0001) is intentionally out of scope — see §9 for how the facade delivers value before that decision is made.

Package names below use `com.acme.flags` as a placeholder — replace `com.acme` with your actual base package.

---

## 1. Design principles

These carry forward from ADR 0001 and the facade sketch's guardrails, made explicit as constraints on this design:

1. **The facade consumes engine state; it never owns a second copy of it.** No independent flag storage, no independent rollout math, no second admin UI (see §9's guardrail restatement).
2. **The core is engine-agnostic at compile time.** No module a service depends on for day-to-day flag checks may declare a compile-time dependency on Unleash's or GOFF's SDK. Vendor dependencies live only in adapter modules added later.
3. **Flag ownership is decentralized to match service ownership** (§6) — there is no single, org-wide flag enum.
4. **The public interface evolves additively.** Breaking `FeatureFlags` or `FlagEngine` has fleet-wide blast radius (every service depends on it); changes must default to additive (§7).

---

## 2. Module layout

| Module | Purpose | Vendor SDK deps | Ships |
|---|---|---|---|
| `flags-api` | `FeatureFlags`, `FlagKey`, `FlagContext`, `FlagMetadata`, `ConfigKey` — the public contract | None | Now |
| `flags-noop` | `NoOpFlagEngine`, `InMemoryFlagEngine` — default/bridge implementations | None | Now |
| `flags-test-fixtures` | `FakeFeatureFlags` — test double for *consumers* of the facade | None | Now |
| `flags-contract-test` | `FlagEngineContractTest` — abstract suite every `FlagEngine` impl must pass | None (test-scope only) | Now |
| `flags-spring-boot-starter` | Auto-configuration; wires the active engine via a property | None (depends on `flags-api` + whichever engine module is on the classpath) | Now |
| `flags-engine-unleash` | `UnleashFlagEngine` adapter | Unleash Java SDK | After ADR 0002, if Unleash is chosen |
| `flags-engine-goff` | `GoffFlagEngine` adapter | OpenFeature Java SDK + GOFF provider | After ADR 0002, if GOFF is chosen |

The first five modules are fully buildable, testable, and consumable by services today. This is the mechanism by which "design the facade before the engine" actually works as an engineering strategy, not just a planning exercise.

**Update:** `flags-engine-goff`'s module shell — POM entry, package structure, and an inert `GoffFlagEngine` that throws `UnsupportedOperationException` — already shipped on `main` ahead of ADR 0002, as a pre-ratification spike (see `flags-engine-goff/README.md`). The "After ADR 0002" ship date above refers to the *functioning* adapter (real OpenFeature/GOFF SDK wiring, vendor dependencies uncommented), not the module's existence.

---

## 3. Core interfaces

### `FlagKey`

```java
package com.acme.flags.api;

public interface FlagKey {
    String key();
    FlagMetadata metadata();
}
```

### `FlagMetadata`

A refinement over the sketch: the sketch's `FailurePolicy` conflated "what value" with "what it means." Here they're split — `defaultValue` is what gets returned on failure; `failureSemantics` is a human-readable label validated against it, used by tooling/governance to catch mismatches (e.g., a `FAIL_CLOSED` flag whose `defaultValue` is `true` should fail a lint check).

```java
package com.acme.flags.api;

public record FlagMetadata(
    String owner,
    String ticket,
    boolean defaultValue,
    FailureSemantics failureSemantics,
    Duration expiresAfter          // null = permanent (config, not a rollout flag)
) {
    public static Builder builder() { return new Builder(); }
    // standard builder — owner/ticket/defaultValue/failureSemantics required, expiresAfter optional
}

public enum FailureSemantics { FAIL_OPEN, FAIL_CLOSED }
```

### `FlagContext`

```java
package com.acme.flags.api;

public final class FlagContext {
    private final String tenantId;
    private final String userId;
    private final Map<String, String> traits;

    public static FlagContext current() {
        // resolves from FlagContextHolder (a ThreadLocal), populated by flags-spring-boot-starter's
        // FlagContextFilter from the active FlagContextResolver bean; see the Slice A spec for the
        // full design: docs/superpowers/specs/2026-07-09-flags-v1-slice-a-facade-hardening-design.md
    }

    public static FlagContext forTenant(String tenantId) { ... }
    public static FlagContext anonymous() { ... }   // background jobs, schedulers — no request in flight

    public Map<String, String> toMap() { ... }
}
```

### `FeatureFlags` — the public API

Only three abstract methods; everything else is a default method built on top of them. This is deliberate — see §7's versioning rules for why.

```java
package com.acme.flags.api;

public interface FeatureFlags {

    boolean isEnabled(FlagKey flag, FlagContext context);
    <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue, FlagContext context);
    <T> T getConfigValue(ConfigKey<T> key, FlagContext context);

    default boolean isEnabled(FlagKey flag) {
        return isEnabled(flag, FlagContext.current());
    }

    default <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue) {
        return getVariant(flag, type, defaultValue, FlagContext.current());
    }

    default <T> T getConfigValue(ConfigKey<T> key) {
        return getConfigValue(key, FlagContext.current());
    }
}
```

### `ConfigKey<T>`

```java
package com.acme.flags.api;

public interface ConfigKey<T> extends FlagKey {
    Class<T> type();
    T defaultValue();
}
```

v1 scope note: `getConfigValue` is implemented as a thin wrapper over `evaluateVariant` returning a typed payload (string/JSON). Full remote-config semantics (Flagsmith-style key/value store) are explicitly deferred — neither Unleash nor GOFF need it as a first-class concept, and it's not required by either finalist engine.

### `FlagEngine` SPI

```java
package com.acme.flags.spi;

public interface FlagEngine {
    boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue);
    <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue);
}
```

**Contract every implementation must satisfy** (enforced by `flags-contract-test`, §8):

- MUST return `defaultValue` for an unknown/unrecognized key — never throw for "flag doesn't exist."
- MAY throw only for transport/connectivity failure (engine unreachable). The facade (`DefaultFeatureFlags`) is responsible for catching this and applying `FlagMetadata.defaultValue()` — engines do not need their own fallback logic.
- MUST treat a `null` or empty context map as equivalent to "no targeting context available," not as an error.
- MUST be safe for concurrent calls from multiple threads.

---

## 4. Reference implementations shipped without an engine

### `NoOpFlagEngine`

Always returns the caller-supplied default. Every flag check behaves exactly like `FlagMetadata.defaultValue()` — i.e., the flag exists in code and is exercised at compile time and in tests, but has no live toggle capability yet.

```java
public final class NoOpFlagEngine implements FlagEngine {
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        return defaultValue;
    }
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        return defaultValue;
    }
}
```

### `InMemoryFlagEngine`

Config-driven overrides, sourced from Spring properties (`flags.overrides.<key>=true`) or, in AWS, from Parameter Store / Secrets Manager via the standard Spring Cloud AWS config import. This is a legitimate bridge deployment mode, not just a test fixture — it lets a team ship a real (if static, non-percentage) rollout mechanism before Unleash/GOFF is running.

```java
public final class InMemoryFlagEngine implements FlagEngine {
    private final Map<String, Boolean> booleanOverrides;
    private final Map<String, Object> variantOverrides;

    public InMemoryFlagEngine(FlagOverridesProperties props) { ... }

    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        return booleanOverrides.getOrDefault(key, defaultValue);
    }
    // evaluateVariant analogous
}
```

Both ship in `flags-noop` and both pass `FlagEngineContractTest`.

---

## 5. Per-service flag registries — not a global enum

The facade sketch showed one illustrative `Feature` enum; this design deliberately rejects a single, shared, org-wide enum. A central `Feature` enum would become a cross-team merge-conflict hotspot and would require redeploying every consuming service to add one team's flag — the opposite of microservice independence.

Instead, **each service defines its own registry**, implementing the shared `FlagKey` interface from `flags-api`:

```java
// lives in checkout-service's own codebase — not in flags-api
package com.acme.checkout.flags;

public enum CheckoutFlags implements FlagKey {

    NEW_CHECKOUT_FLOW(FlagMetadata.builder()
        .owner("checkout-team")
        .ticket("CHECKOUT-1421")
        .defaultValue(false)
        .failureSemantics(FailureSemantics.FAIL_CLOSED)
        .expiresAfter(Duration.ofDays(90))
        .build());

    private final FlagMetadata metadata;
    CheckoutFlags(FlagMetadata metadata) { this.metadata = metadata; }

    public String key() { return "checkout." + name().toLowerCase().replace('_', '-'); }
    public FlagMetadata metadata() { return metadata; }
}
```

**Naming convention:** flag keys are namespaced by service (`checkout.new-checkout-flow`, `billing.rate-limit-override`) to avoid collisions in the shared vendor backend once an engine exists.

**Org-wide visibility** is intentionally *not* rebuilt at the facade layer — that would violate principle 1 (never own a second copy of state). Once an engine is chosen, its own admin UI (Unleash or GOFF's) is the org-wide source of truth for what flags exist and their live state. The per-service `FlagKey` enums are typed handles into that same backend, not a competing catalog.

---

## 6. Versioning and compatibility strategy

`flags-api` and `flags-spi` are versioned independently of any service; every service takes a dependency on them, so a breaking change has fleet-wide blast radius.

| Change | Version bump | Notes |
|---|---|---|
| New default method added to `FeatureFlags` | MINOR | Existing `FlagEngine` implementations are unaffected — defaults only call the existing abstract methods |
| Existing abstract method signature changed on `FeatureFlags` or `FlagEngine` | MAJOR | Avoid; requires coordinated update of every engine adapter |
| New method added to `FlagEngine` (non-default) | MAJOR | Breaks every existing adapter — prefer adding as a default method on the interface instead |
| A service adds a new `FlagKey` entry in its own registry | N/A | Service-owned; not a `flags-api` release at all |
| Bug fix, no interface change | PATCH | |

**Pre-1.0 while no engine adapter exists.** `flags-api` ships as `0.x` until an engine adapter (`flags-engine-unleash` or `flags-engine-goff`) has been built and passed `flags-contract-test` — signaling the interfaces are still allowed to shift based on what an actual adapter implementation reveals. Cut `1.0.0` once the first real adapter exists and its ADR (0002) is accepted.

**Governance PR checklist**, enforced by review (not tooling, initially) for any new `FlagKey` entry: `owner` and `ticket` set, `defaultValue` matches the flag's actual pre-created state in the vendor backend, `failureSemantics` chosen deliberately rather than copy-pasted, `expiresAfter` set unless it's genuinely permanent config.

---

## 7. Testing strategy

- **Facade unit tests** — `DefaultFeatureFlags`'s own logic (context resolution, exception-to-default-value fallback, metrics emission) tested against a configurable `MockFlagEngine` test double (throws on demand, returns fixed values) — separate from `FakeFeatureFlags`, which is for *consumers* of the facade to use in *their* tests.
- **`FlagEngineContractTest`** — an abstract JUnit base class encoding the SPI contract from §3. Every implementation, present and future, extends it:

```java
public abstract class FlagEngineContractTest {
    protected abstract FlagEngine engine();

    @Test void unknownKeyReturnsDefault() {
        assertEquals(true, engine().evaluateBoolean("does-not-exist", Map.of(), true));
    }

    @Test void nullContextTreatedAsEmpty() {
        assertDoesNotThrow(() -> engine().evaluateBoolean("any-key", null, false));
    }

    @Test void concurrentEvaluationIsSafe() {
        // N threads calling evaluateBoolean concurrently — assert no exceptions, no corrupted state
    }
}

class NoOpFlagEngineTest extends FlagEngineContractTest {
    protected FlagEngine engine() { return new NoOpFlagEngine(); }
}
```

  When `flags-engine-unleash` or `flags-engine-goff` is built later (ADR 0002), its test class extends the same base — holding it to the identical bar without `flags-api` ever depending on either SDK.

- **Deferred until an engine is chosen:** integration tests against a real backend (e.g., Testcontainers running Unleash + Postgres, or a GOFF relay-proxy container). The contract test suite is what makes this safe to defer — the adapter's unit-level correctness is already pinned down.

---

## 8. Rollout plan that doesn't wait on ADR 0002

1. **Now:** implement `flags-api`, `flags-noop`, `flags-test-fixtures`, `flags-contract-test`, `flags-spring-boot-starter`. Publish as `0.x`.
2. **Services integrate immediately** against `FeatureFlags` and their own `FlagKey` registries, wired to `NoOpFlagEngine` (static defaults) or `InMemoryFlagEngine` (config-driven overrides, including via AWS Parameter Store) as a real, if non-percentage, rollout mechanism.
3. **ADR 0002 picks the engine.** Build the corresponding `flags-engine-*` module, pass `flags-contract-test`, cut `1.0.0`.
4. **Flip `flags.engine=<chosen>` per service.** Zero call-site changes — this is the same swap mechanic demonstrated in `feature-flags-use-cases.md`'s in-house facade Use Case 3, just exercised in the opposite direction (no engine → an engine, instead of engine A → engine B).

This is the concrete payoff of designing the facade first: typed API, governance, and consistent failure-policy behavior land in every service now, and the eventual engine decision becomes a config change layered on top rather than a blocking dependency for starting the work.

**Superseded by:** [`feature-flags-v1-roadmap.md`](feature-flags-v1-roadmap.md) — this plan predates ADR 0002 Part B's B1/B2/B3 scope additions; the roadmap sequences the full remaining v1 work (including B1/B2/B3) against its real blockers and links a detailed spec per slice.

---

## 9. Tech stack

### Decided

| Concern | Decision | Notes |
|---|---|---|
| Build tool | **Maven** — multi-module reactor (`flags-api`, `flags-noop`, `flags-test-fixtures`, `flags-contract-test`, `flags-spring-boot-starter` as sibling modules under a parent POM; `flags-engine-*` added post-ADR-0002) | A `flags-bom` module pins consistent versions across all published artifacts |
| CI/CD | **GitHub Actions** | See pipeline shape below |
| Artifact hosting | **Self-hosted Nexus** | Already used by the target monorepo — `flags-*` artifacts publish alongside existing internal libraries, so consuming services add no new repository configuration |
| JDK | **Java 21 (LTS)** | `maven.compiler.release=21` in the parent POM; unlocks virtual threads and pattern matching for switch alongside the records already used in `FlagMetadata` |
| Testing | **JUnit 5 (Jupiter) + AssertJ** | `flags-contract-test`'s `FlagEngineContractTest` base class and all facade unit tests target Jupiter; AssertJ for fluent assertions |
| Metrics | **Micrometer** | Wired into `DefaultFeatureFlags`'s `MeterRegistry` usage (§3/§7); CloudWatch registry available out of the box for the AWS stack |
| Logging | **SLF4J API only** | No bound implementation in any `flags-*` module — consuming services supply their own Logback/Log4j2 binding |

### CI pipeline shape (GitHub Actions)

**On pull request:**
1. `mvn verify` — compiles the reactor, runs unit tests, and runs `flags-contract-test` against every `FlagEngine` implementation present on the classpath (currently `NoOpFlagEngine` and `InMemoryFlagEngine`; `UnleashFlagEngine`/`GoffFlagEngine` join automatically once their modules exist).
2. ArchUnit check — fails the build if `flags-api` or `flags-spi` imports anything from an Unleash or GOFF package, enforcing design principle 2 mechanically rather than by convention.
3. Binary/source compatibility gate (`japicmp` or `revapi`) against the latest version published to Nexus — fails the build on an accidental breaking change to `FeatureFlags` or `FlagEngine`, enforcing the SemVer table in §6.

**On merge to main:**
- Same checks, then `mvn deploy` — publishes a `SNAPSHOT` to Nexus on every merge; a tagged release publishes the corresponding versioned artifact (`0.x` until the first engine adapter passes its contract tests, per §6).

The full tech stack is now confirmed — nothing left open in this section.

---

## References

- `../adr/0001-adopt-in-house-facade-alongside-flag-engine.md`
- `feature-flags-facade-sketch.md` — narrative walkthrough this document formalizes
- `feature-flags-comparison.md`
- `feature-flags-use-cases.md`
- `feature-flags-executive-summary.md`
