# v1 Slice C — Complete the `flags-engine-goff` Adapter

## Status

Draft — **blocked**. Requires ADR 0002 to be *Accepted* with GO Feature Flag as the ratified engine (see [Slice B](2026-07-09-flags-v1-slice-b-ratification-evidence-design.md) for the evidence that decision depends on). Part of the [v1 roadmap](../../feature-flags-v1-roadmap.md).

## Purpose

Turn the inert `flags-engine-goff` skeleton (merged to `main` via PR #1, ahead of ratification, specifically so this slice has no scaffolding work left to do) into a real, functioning `FlagEngine` implementation, and cut `1.0.0`.

**This entire document is void if Unleash is ratified instead of GOFF.** There is no `flags-engine-unleash` skeleton — Slice C's Gate-1/Gate-2-failure counterpart would be an unwritten "build `flags-engine-unleash` from scratch" spec with a materially different shape (no pre-ratification scaffolding exists for it, unlike GOFF's).

---

## Requirements

The five-step checklist already committed to in [`flags-engine-goff/README.md`](../../../flags-engine-goff/README.md), taken as this slice's requirements verbatim:

1. Uncomment the `dev.openfeature:sdk` and GO Feature Flag provider dependencies in `flags-engine-goff/pom.xml`; confirm their real coordinates/versions (currently left as "confirm at that point" in the commented block — **not yet pinned anywhere**, this slice's first real decision).
2. Implement `GoffFlagEngine.evaluateBoolean`/`evaluateVariant` (`flags-engine-goff/src/main/java/com/acme/flags/engine/goff/GoffFlagEngine.java`) against a configured OpenFeature `Client`, per the TODO comments already in that file (they sketch the exact mapping: `client.getBooleanValue(...)`, typed-getter dispatch by `Class<T>`, and a `Map<String,String> → EvaluationContext` translation using `userId` or `tenantId` from the context map as the targeting key).
3. Remove `@Disabled` from `GoffFlagEngineContractTest` and make it pass against the shared `FlagEngineContractTest` base.
4. Wire it into `flags-spring-boot-starter`: add a `@Bean @ConditionalOnProperty(name = "flags.engine", havingValue = "goff")` method to `FlagsAutoConfiguration`, which adds a new `flags-engine-goff` dependency to `flags-spring-boot-starter`.
5. Add `flags-engine-goff` to `flags-bom`'s dependency management (currently absent — verified).

## Gaps and dependencies on other slices

- **Depends on Slice A's exception-type decision.** If Slice A ships `FlagEngineUnavailableException`, this adapter's error mapping must target that type — the OpenFeature client's transport/connection exceptions need to be caught and re-thrown as `FlagEngineUnavailableException`, not left as whatever raw exception the OpenFeature SDK throws (which would either fail `DefaultFeatureFlags`'s narrowed catch, or, if Slice A hasn't shipped yet, fall under the current broad `RuntimeException` catch — this slice should not assume Slice A's ordering and must check what's actually shipped when this work starts).
- **Vendor coordinates are unconfirmed.** Pin real `dev.openfeature:sdk` and GO Feature Flag provider versions as part of step 1 — don't carry the placeholder forward.
- **Contract-test coverage becomes real here, not before.** This is the first `FlagEngine` implementation with an actual failure mode (a real network/transport dependency) — per Slice A's spec, the "engines only throw the declared exception type" contract test that couldn't be meaningfully written against `NoOpFlagEngine`/`InMemoryFlagEngine` becomes addable now, using this adapter to simulate a transport failure (e.g. mocking or misconfiguring the OpenFeature client). Add it here if Slice A deferred it.
- **May not be independently releasable as `1.0.0`.** ADR 0002's Consequences section states "B1 and B2 are additional build scope in the first release" — completing this slice alone may not satisfy that commitment. See the [v1 roadmap](../../feature-flags-v1-roadmap.md) §3 for the open sequencing question this raises; this spec doesn't resolve it.
- **`GoffFlagEngineContractTest` passing is the actual completion signal**, not "code compiles" — per `feature-flags-facade-design.md` §6, `flags-api` stays on the `0.x` line specifically until an adapter passes `flags-contract-test`; that's the trigger for cutting `1.0.0`, not a calendar date or a subjective sense of doneness.

## Out of scope

- `flags-engine-unleash` — an entirely separate, unwritten spec if ratification goes the other way.
- Real relay-proxy vs. embedded-mode deployment decisions for any specific consuming service — that's Slice B's Gate 1 evidence informing the ratification call, not this slice's concern; this slice implements the adapter correctly for whichever mode(s) the ratified decision requires supporting.

## References

- [v1 roadmap](../../feature-flags-v1-roadmap.md)
- [Slice A — Facade hardening](2026-07-09-flags-v1-slice-a-facade-hardening-design.md) — the exception-type dependency
- [Slice B — Ratification evidence](2026-07-09-flags-v1-slice-b-ratification-evidence-design.md) — the decision this slice is gated on
- `../../../flags-engine-goff/README.md` — the checklist this spec formalizes
- `../../../flags-engine-goff/src/main/java/com/acme/flags/engine/goff/GoffFlagEngine.java`, `pom.xml`
- `../../feature-flags-facade-design.md` §6 — the `1.0.0` cut trigger
- `../../../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md` — Consequences section, "B1 and B2 are additional build scope in the first release"
