# Flags Facade — Phase 1 Implementation Design (vendor-free modules)

## Status

Approved for implementation. Scopes the first buildable slice of the "alongside" facade accepted in [ADR 0001](../../../adr/0001-adopt-in-house-facade-alongside-flag-engine.md) and specified in [`feature-flags-facade-design.md`](../../feature-flags-facade-design.md) (the canonical prose spec) and its diagram companion [`feature-flags-facade-design-visual.md`](../../feature-flags-facade-design-visual.md).

## Purpose

Move the facade from documentation to a real, buildable, testable Maven project. This is the first implementation pass. It builds exactly the "ships now" slice from the design doc's own rollout plan (§8, step 1) — the modules that need no engine decision — and stops there.

## Explicit scope decisions

These were confirmed during design and are binding for this pass:

1. **ADR 0002 is not folded in.** ADR 0002 (engine selection + operating-model amendments B1–B4) is still Draft/unratified. This implementation follows `feature-flags-facade-design.md` as written, without the governance job (B1), interaction scan (B2), or resilience-contract (B3) changes. Those remain future work, gated on ADR 0002 being accepted.
2. **No engine adapters.** `flags-engine-unleash` and `flags-engine-goff` are out of scope — they depend on ADR 0002's engine pick, which is not ratified. `NoOpFlagEngine` and `InMemoryFlagEngine` are the only `FlagEngine` implementations built now.
3. **Package/groupId:** keep the design doc's placeholder, `com.acme.flags`, as-is (sandbox project, not tied to a real org).
4. **`DefaultFeatureFlags` lives in `flags-api`.** The design doc's module table (§2) doesn't explicitly place it. Resolution: it belongs with the interfaces, not a separate `flags-core` module, because it is engine-agnostic, has no vendor dependency, and is needed regardless of which engine is eventually chosen. Its only non-`flags-api` dependency is Micrometer.
5. **CI is narrower than design doc §9's full pipeline.** Build `mvn verify` + an ArchUnit rule now. Defer `japicmp`/`revapi` (binary-compat gate) and the Nexus publish step — there is no prior published artifact to diff against yet and no real Nexus target in this sandbox, so both would be dead weight. Tracked as a follow-up.

## Repo layout

Root `pom.xml` acts as both parent (dependency/plugin management) and reactor (`<modules>`), sibling to the existing `adr/` and `docs/` directories:

```
pom.xml                       # parent + reactor
flags-bom/                    # dependencyManagement-only artifact (pom packaging)
flags-api/                    # public contract + DefaultFeatureFlags
flags-noop/                   # NoOpFlagEngine, InMemoryFlagEngine
flags-test-fixtures/          # FakeFeatureFlags
flags-contract-test/          # FlagEngineContractTest base class
flags-spring-boot-starter/    # auto-configuration
.github/workflows/ci.yml
```

## Modules

### `flags-api`

Package `com.acme.flags.api`:
- `FlagKey` — `key()`, `metadata()`.
- `FlagMetadata` (record) — `owner`, `ticket`, `defaultValue`, `failureSemantics`, `expiresAfter` (nullable = permanent), with a builder.
- `FailureSemantics` (enum) — `FAIL_OPEN`, `FAIL_CLOSED`.
- `FlagContext` — `current()` (resolves request-scoped tenant/security context), `forTenant(String)`, `anonymous()`, `toMap()`.
- `ConfigKey<T>` extends `FlagKey` — `type()`, `defaultValue()`.
- `FeatureFlags` — 3 abstract methods (`isEnabled`, `getVariant`, `getConfigValue`, each taking an explicit `FlagContext`) + 3 default no-context overloads that resolve `FlagContext.current()`. Only 3 abstract methods, by design, so future additions stay additive (MINOR bump, per §6/§7 of the design doc).
- `DefaultFeatureFlags` — the reference `FeatureFlags` implementation: delegates to an injected `FlagEngine`, catches transport-failure exceptions and falls back to `FlagMetadata.defaultValue()`, emits a `feature_flag.evaluated` Micrometer counter tagged by flag key and result.

Package `com.acme.flags.spi`:
- `FlagEngine` — `evaluateBoolean(String, Map<String,String>, boolean)`, `evaluateVariant(String, Map<String,String>, Class<T>, T)`. Contract (enforced by `flags-contract-test`): unknown key returns the supplied default rather than throwing; only transport/connectivity failure may throw; `null`/empty context is equivalent to no context; must be thread-safe.

Dependencies: `io.micrometer:micrometer-core` only. No Spring, no vendor SDK.

### `flags-noop`

- `NoOpFlagEngine` — always returns the caller-supplied default.
- `InMemoryFlagEngine` — config-driven overrides from `FlagOverridesProperties` (`flags.overrides.<key>=true`, backed by Spring `@ConfigurationProperties`, works with Spring Cloud AWS config import for Parameter Store/Secrets Manager in a real deployment).

Depends on `flags-api`. Test sources here also contain `NoOpFlagEngineTest` and `InMemoryFlagEngineTest`, each extending `FlagEngineContractTest` from `flags-contract-test`.

### `flags-test-fixtures`

- `FakeFeatureFlags` — in-memory `FeatureFlags` test double for *consumers* of the facade (`set(FlagKey, boolean)` plus a variant/config equivalent), falling back to the flag's own `FlagMetadata.defaultValue()` when unset. Distinct from `MockFlagEngine` (see Testing strategy) — this one is for services that depend on the facade, not for testing the facade itself.

Depends on `flags-api`.

### `flags-contract-test`

- Abstract `FlagEngineContractTest` (JUnit 5), with `protected abstract FlagEngine engine()` and three tests: `unknownKeyReturnsDefault`, `nullContextTreatedAsEmpty`, `concurrentEvaluationIsSafe`. Ships as a normal (non-test-scoped) artifact so other modules can depend on it in *their* test scope and extend the base class.

Depends on `flags-api` + JUnit 5.

### `flags-spring-boot-starter`

- `@AutoConfiguration` that wires a `FeatureFlags` bean (`DefaultFeatureFlags`, given whichever `FlagEngine` bean is active) selected by the `flags.engine` property. Valid values today: `noop` (default) and `in-memory`. Adding `unleash`/`goff` later is purely a matter of a new adapter module being present on the classpath plus a new property value — no change to this module.

Depends on `flags-api`, `flags-noop`, Spring Boot autoconfigure.

### `flags-bom`

`pom`-packaged artifact whose `dependencyManagement` pins consistent versions across all `flags-*` artifacts, so a consuming service imports one BOM instead of managing versions per module (per design doc §9).

## Testing strategy

- JUnit 5 (Jupiter) + AssertJ, per the design doc's decided tech stack.
- `DefaultFeatureFlags` unit tests use a small hand-rolled `MockFlagEngine` test double (in `flags-api`'s own test sources) that can be told to throw or return fixed values — verifying context resolution, exception-to-default fallback, and metrics emission. This is separate from `FakeFeatureFlags`, which is for facade *consumers*.
- `FlagEngineContractTest` runs against every `FlagEngine` implementation that exists today: `NoOpFlagEngine`, `InMemoryFlagEngine`. Future engine adapters extend the same base class and are held to the identical bar without `flags-api` ever depending on a vendor SDK.
- Integration tests against a real backend (Testcontainers, relay-proxy containers, etc.) stay deferred until an engine adapter exists, per the design doc — nothing to integration-test yet.

## CI

`.github/workflows/ci.yml`, on push/PR:
1. `mvn -B verify` — compiles the reactor, runs unit tests and the full contract-test suite against every `FlagEngine` present (`NoOpFlagEngine`, `InMemoryFlagEngine` today; future adapters join automatically once their modules exist).
2. An ArchUnit rule (test in `flags-api`) asserting classes in `com.acme.flags.api`/`com.acme.flags.spi` only import an allow-list of packages (`java.*`, `com.acme.flags.*`, `io.micrometer.*`). Written as an allow-list rather than a vendor-package denylist so it catches a future accidental Unleash/GOFF import even before those packages are known to the build.

Explicitly **not** built in this pass (follow-up, once a real release exists to diff against and a real artifact repository target exists): the `japicmp`/`revapi` binary-compatibility gate and the Nexus `mvn deploy` step described in design doc §9.

## Versioning

`0.1.0-SNAPSHOT` to start. Java 21 (LTS), Maven multi-module reactor. Stays on the `0.x` line until an engine adapter exists and passes `flags-contract-test`, per §6 of the design doc — unchanged by this pass, since no adapter is built here.

## Out of scope / follow-ups

- Engine adapters (`flags-engine-unleash`, `flags-engine-goff`) — blocked on ADR 0002 ratification.
- ADR 0002's B1 (stale-flag governance job), B2 (interaction scan), B3 (resilience contract for context resolution) — blocked on ADR 0002 being accepted, not part of this design.
- `japicmp`/`revapi` compatibility gate and Nexus publish step — no baseline/target to build against yet.
- Per-service example `FlagKey` registry (e.g. `CheckoutFlags`) — that's consumer code living in each service's own codebase, not part of this library.
