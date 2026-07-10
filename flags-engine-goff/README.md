# flags-engine-goff (pre-ratification skeleton)

OpenFeature / GO Feature Flag (GOFF) implementation of the `flags-api` `FlagEngine` SPI.

**Status: skeleton, not wired.** GOFF is the *recommended* engine in
[ADR 0002](../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md), but that ADR is
*Proposed (draft)* and blocks implementation start until it is ratified (see the
[ratification checklist](../adr/0002-ratification-checklist.md)). This module exists so the adapter's
shape, its place in the reactor, and its conformance gate are ready the moment the engine is
ratified — it does **not** evaluate flags yet. It ships on `main` today as this inert skeleton
(merged via #1); only the real evaluation logic below still waits on ratification.

## What's here
- `GoffFlagEngine` — implements `FlagEngine`; both methods throw `UnsupportedOperationException`.
  The intended OpenFeature mapping (`client.getBooleanValue(...)`, typed-getter dispatch, and the
  `Map<String,String> → EvaluationContext` translation) is spelled out in TODO comments.
- `GoffFlagEngineContractTest` — extends the shared `FlagEngineContractTest`; `@Disabled` until the
  engine is implemented.

The `pom.xml` carries **no** external engine dependency; the real `dev.openfeature:sdk` + GO Feature
Flag provider dependencies are present only as a commented-out block, so the reactor commits to no
engine choice ahead of ratification.

## Completing the adapter (on ADR 0002 ratification)
1. Uncomment the `dev.openfeature:sdk` + GO Feature Flag provider dependencies in `pom.xml` and
   confirm their coordinates/versions.
2. Implement `GoffFlagEngine`'s `evaluateBoolean` / `evaluateVariant` against a configured
   OpenFeature `Client`, per the TODO comments; add the `Map<String,String> → EvaluationContext`
   translation.
3. Remove the `@Disabled` from `GoffFlagEngineContractTest` and make it pass.
4. Wire it into the Spring Boot starter: add a
   `@Bean @ConditionalOnProperty(name = "flags.engine", havingValue = "goff")` method to
   `FlagsAutoConfiguration` (this adds a `flags-engine-goff` dependency to
   `flags-spring-boot-starter`).
5. Add `flags-engine-goff` to `flags-bom`'s dependency management once ADR 0002 is *Accepted*.
