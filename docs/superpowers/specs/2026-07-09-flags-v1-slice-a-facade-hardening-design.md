# v1 Slice A — Facade Hardening (`FlagContext` resolution + exception narrowing)

## Status

Draft — ready to implement, unblocked by ADR 0002 ratification. Part of the [v1 roadmap](../../feature-flags-v1-roadmap.md). Closes the two "Action required" findings in [`feature-flags-research-gaps-v2.md`](../../feature-flags-research-gaps-v2.md) (Threads 4 and 6) and the "easy half" of [ADR 0002](../../../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md) Part B, B3.

## Purpose

Two things are stubbed or under-specified in the code shipped today:

1. `FlagContext.current()` (`flags-api/src/main/java/com/acme/flags/api/FlagContext.java`) unconditionally returns `anonymous()`. No request-scoped tenant/user/trait resolution exists.
2. `DefaultFeatureFlags` (`flags-api/src/main/java/com/acme/flags/api/DefaultFeatureFlags.java:27,42`) catches `RuntimeException` broadly around every engine call. This can't distinguish the one case the `FlagEngine` SPI documents as legitimate ("MAY throw only for transport/connectivity failure," `feature-flags-facade-design.md` §3) from a genuine bug inside an engine adapter.

This slice closes both, and in doing so resolves a real architecture conflict that neither of the existing design docs actually settles (§A1 below).

---

## A1 — `FlagContext.current()`

### The conflict this must resolve

The only concrete sketch of `current()` (`feature-flags-facade-sketch.md` §3) calls `SecurityContextHolder.getContext().getAuthentication()` directly — a Spring Security import inside `com.acme.flags.api`. But `VendorFreedomArchTest` (`flags-api/src/test/java/com/acme/flags/api/VendorFreedomArchTest.java`, already merged, already running in CI) mechanically forbids `com.acme.flags.api..`/`com.acme.flags.spi..` from importing anything outside `java..`, `io.micrometer..`, `org.slf4j..`, and their own packages. Porting the sketch as written fails CI. `feature-flags-facade-design.md` §3 doesn't resolve this — it replaces the sketch's code with a comment, `// resolves from request-scoped tenant/security context; see §6 for trait sourcing`, and §6 is "Versioning and compatibility strategy," which contains no trait-sourcing content. This is a dead cross-reference, not a design decision. Nothing in the shipped docs actually specifies how `flags-api` — which by design cannot depend on Spring — is supposed to read Spring-managed request context.

### Recommended design

Split the responsibility across the two modules that can each legally hold it:

- **`flags-api` (vendor-free):**
  - `FlagContextHolder` — a new class next to `FlagContext`, backed by a plain `ThreadLocal<FlagContext>` (`java.lang.ThreadLocal` — no new dependency). Static `set(FlagContext)`, `get()` (returns `Optional<FlagContext>` or `null`), `clear()`.
  - `FlagContext.current()` reads `FlagContextHolder.get()`, falling back to `anonymous()` if unset — preserving today's documented behavior for background jobs/schedulers with no request in flight (`FlagContext.anonymous()`'s existing javadoc already describes this use case).
  - `FlagContextResolver` — a new SPI interface (same shape as `FlagEngine`): `FlagContext resolve()`. This is the extension point a consuming service implements to supply its own tenant/user/trait-resolution logic (reading its own `SecurityContext`, JWT claims, whatever it actually has) — `flags-api` never needs to know what a "tenant" or "auth" concept looks like for a given service, mirroring how `FlagEngine` already keeps `flags-api` from needing to know what Unleash or GOFF look like.
- **`flags-spring-boot-starter` (vendor-aware, that's its whole job):**
  - A `jakarta.servlet.Filter` bean, `@ConditionalOnWebApplication(type = Type.SERVLET)` (guards non-web consumers, e.g. background workers, from getting a dead filter registered), that calls the active `FlagContextResolver` bean at request start, populates `FlagContextHolder`, and clears it in a `finally` block at request end (mandatory — a `ThreadLocal` left set on a pooled or virtual thread leaks context into the next request).
  - A default `FlagContextResolver` bean (`@ConditionalOnMissingBean`) that returns `FlagContext.anonymous()`, so a service that hasn't wired its own resolver yet gets today's existing behavior, not a startup failure.
  - New dependency this module doesn't have today: `jakarta.servlet-api`, `provided` scope (the servlet container is supplied by the consuming Spring Boot app at runtime — this is the same pattern Spring's own `spring-boot-autoconfigure` uses for servlet-aware autoconfiguration).

### Gaps this spec does not resolve

- **Real trait semantics can't be validated end-to-end.** No consuming service or entitlements integration exists anywhere in this repo. Scope here is the *mechanism* (holder, resolver SPI, filter, default no-op resolver) plus tests proving it round-trips correctly — not real tenant/plan data, which only a real consuming service's `FlagContextResolver` implementation could supply.
- **This design is a recommendation, not a ratified decision.** It resolves the ArchUnit conflict cleanly and follows the SPI pattern already established for `FlagEngine`, but it hasn't been reviewed the way ADR 0001/0002 were. Whoever picks this slice up should treat the design above as a strong starting point, not a foregone conclusion — in particular, a `HandlerInterceptor` instead of a `Filter` is a reasonable alternative if the team prefers staying entirely within `spring-webmvc` rather than adding a raw servlet dependency.
- **`facade-design.md` §3's dead "see §6 for trait sourcing" cross-reference should be updated** to point at this spec once it exists, rather than continuing to point at the wrong section.

---

## A2 — Exception narrowing

### The change

Introduce `FlagEngineUnavailableException extends RuntimeException` in `com.acme.flags.spi` (next to `FlagEngine` — it's part of the SPI contract, not an implementation detail). Update `FlagEngine`'s javadoc to name it explicitly: engines **MUST** throw `FlagEngineUnavailableException` (not a bare `RuntimeException`) for the transport/connectivity-failure case the contract already documents. Narrow `DefaultFeatureFlags.isEnabled`/`getVariant` (`flags-api/src/main/java/com/acme/flags/api/DefaultFeatureFlags.java:27,42`) to `catch (FlagEngineUnavailableException e)`.

Effect: a genuine transport failure still falls back to `FlagMetadata.defaultValue()` exactly as today. A bug inside an engine adapter (e.g. a stray `NullPointerException`) now propagates instead of being silently reclassified as "engine unavailable" — directly closing the gap `docs/feature-flags-research-gaps-v2.md` Thread 6 identified (the current broad catch matches the "Catch Generic" anti-pattern, which the cited literature ties to post-release defects).

### Testing

- `DefaultFeatureFlagsTest`'s existing `MockFlagEngine` (a hand-rolled test double already in `flags-api`'s own test sources per `feature-flags-facade-design.md` §7, configurable to throw on demand) is the right place to test this now, without needing Slice C: configure it to throw `FlagEngineUnavailableException` → assert fallback + `outcome=fallback` metric, exactly as today; configure it to throw a different `RuntimeException` (e.g. `NullPointerException`) → assert it propagates uncaught rather than being swallowed.
- **Gap, stated honestly:** a *contract-level* test in `flags-contract-test`'s `FlagEngineContractTest` asserting "every implementation only throws the declared type" isn't realistically addable yet — `NoOpFlagEngine` and `InMemoryFlagEngine` (the only implementations that exist today) have no failure mode to simulate; they're defined to never throw. This becomes meaningfully testable once Slice C gives the suite a real engine adapter with an injectable failure point (e.g. a mockable OpenFeature client). Note this as deferred to Slice C rather than silently dropping it.

### Ripple effects to handle in this slice

- **`GoffFlagEngine`'s javadoc is now inaccurate and must be updated.** It currently states: "behind `DefaultFeatureFlags` — which wraps engine calls in try/catch and falls back to the flag's default — that surfaces as safe, default-valued evaluation rather than a crash." After narrowing, the skeleton's `UnsupportedOperationException` is no longer caught by that clause (it isn't a `FlagEngineUnavailableException`) and would propagate if anything called it live. In practice nothing does yet — `FlagsAutoConfiguration` has no `goff` branch — but the javadoc's factual claim needs correcting regardless, since Slice C's implementer will read it.
- **`facade-design.md` §6's versioning table has no row for this.** The table covers signature changes to `FeatureFlags`/`FlagEngine`, not "what an engine is now contractually required to throw" changes. This is a real gap: `FeatureFlags`'s public methods don't change shape, so by the table's letter this looks like a non-event, but it's a behavioral contract change every future `FlagEngine` implementation must honor. Recommendation for this slice: treat it as a MINOR bump, consistent with `flags-api` still being pre-1.0 ("interfaces are still allowed to shift based on what an actual adapter implementation reveals," §6) — but flag in the PR description that `facade-design.md` §6 should eventually gain an explicit row for "SPI-implementation-contract change, no signature change," since this won't be the last one.

---

## Out of scope / follow-ups

- Wiring a real `FlagContextResolver` for any specific consuming service — that's the consuming service's own implementation, not this repo's.
- The harder half of ADR 0002 B3 (timeout + circuit breaker for a live synchronous entitlements call) — only needed if a future flag genuinely requires resolving a trait that isn't already request-scoped. Not built here; revisit if that need materializes.
- Any change to `flags-engine-goff`'s actual evaluation logic — that's Slice C, gated on ratification.

## References

- [v1 roadmap](../../feature-flags-v1-roadmap.md)
- `../../feature-flags-facade-sketch.md` §3 — the `SecurityContextHolder` sketch this spec supersedes
- `../../feature-flags-facade-design.md` §3, §6, §7 — the sections this spec corrects/extends
- `../../../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md` Part B, B3
- `../../feature-flags-research-gaps-v2.md` Threads 4 and 6
- `../../../flags-api/src/main/java/com/acme/flags/api/FlagContext.java`, `DefaultFeatureFlags.java`
- `../../../flags-api/src/test/java/com/acme/flags/api/VendorFreedomArchTest.java`
- `../../../flags-engine-goff/src/main/java/com/acme/flags/engine/goff/GoffFlagEngine.java`
