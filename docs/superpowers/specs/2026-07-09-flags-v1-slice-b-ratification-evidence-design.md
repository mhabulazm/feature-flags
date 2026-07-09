# v1 Slice B — Ratification Evidence

## Status

Draft — ready to implement, unblocked by ADR 0002 ratification (this slice exists to *produce* the evidence ratification needs, not to depend on it). Part of the [v1 roadmap](../../feature-flags-v1-roadmap.md).

**Implementation status (this pass):**
- **Parameter Store caching evidence is complete and verified** (`flags-noop`) — `InMemoryFlagEngine` empirically shown to add no I/O on the hot path (100,000 calls in ~36ms).
- **Gate 1 is NOT satisfied.** The harness (`flags-benchmark-goff`) compiles against real OpenFeature/GOFF dependencies but has never been run — no Docker in the environment this was built in. Worse than originally planned: the pinned GOFF provider (`0.4.3`) turned out to lack the `EvaluationType.IN_PROCESS`/`REMOTE` toggle entirely (that API only exists from provider `1.0.0`+, which forces a coordinated `dev.openfeature:sdk` bump to `>= 1.16.0` plus a new Chicory WASM runtime dependency — a real architectural decision, not something absorbed into this pass). The harness as shipped can only produce **relay-proxy/REMOTE-mode** evidence — the *secondary* evidence per this spec's own framing — not the embedded/IN_PROCESS mode this spec calls "the primary evidence Gate 1 needs." See `flags-benchmark-goff/README.md` for the full remediation-cost breakdown.
- **Gate 2's runbook** (`../../feature-flags-v1-slice-b-gate2-runbook.md`) is written but not yet executed with a real non-engineer.
- **Net effect:** neither Gate 1 nor Gate 2 can be signed off from this pass alone. What this pass delivers is the mechanism plus an honest accounting of what's still missing — most notably a dependency-stack decision (upgrade the GOFF provider + SDK + WASM runtime, or get this spec's Gate 1 requirement revised to accept relay-proxy-only evidence) that belongs to whoever picks up Slice B next, not to this implementation pass.

## Purpose

[`adr/0002-ratification-checklist.md`](../../../adr/0002-ratification-checklist.md) defines two engine-selection gates and one non-engine blocker that must be resolved before ADR 0002 can move from *Proposed (draft)* to *Accepted*. This slice builds whatever's needed to gather that evidence. **It does not itself ratify anything** — every threshold in the source checklist is explicitly labeled PROPOSED ("no real load figures exist in this repo yet... treat every PROPOSED value as a starting point to confirm or replace at the ratification meeting"), and this spec preserves that framing rather than silently hardening any number into a real target.

---

## Gate 1 — Peak-load evidence

**Checklist's proposed pass criterion:** a single GOFF instance sustains the projected peak flag-evaluation rate with p99 added latency ≤ ~5ms, bounded (not cliff-shaped) behavior past peak, with the facade's caching model in place (evaluations served from already-request-scoped/cached state, not a per-evaluation network call).

**Gap — no target number exists.** There is no projected peak flag-evaluation req/s figure anywhere in this repo; nothing here talks to real traffic. Two ways to close this, and this slice should not silently pick one without saying so:
- Get a real number from whoever owns capacity planning for the eventual consuming services, or
- Build the harness parameterized (peak req/s as a runtime input) and run it across a sweep (e.g. 100 / 1k / 10k / 50k req/s) to produce a latency-vs-throughput curve, so the ratification meeting can read a real number off the curve instead of the harness guessing one.

The second option is recommended — it doesn't block on getting a number from someone first, and it produces strictly more information than a single pass/fail run against a guessed target.

**Gap — deployment mode must be explicit and singular per run.** `feature-flags-research-gaps-v2.md` Thread 5 found GOFF's relay-proxy mode is "structurally a sidecar-shaped network hop," with literature-quantified sidecar overhead of up to 269% higher latency versus in-process handling — a categorically different cost profile from embedded/library mode. `feature-flags-comparison.md`/`feature-flags-use-cases.md` recommend relay-proxy mode for polyglot/multi-tenant use without quantifying that gap. This slice must run (and separately label) at least:
1. **Embedded/library mode** — the mode the checklist's "already-request-scoped/cached state, not a per-evaluation network call" condition actually describes, and the mode GOFF's Tier-1 in-process story rests on. This is the primary evidence Gate 1 needs.
2. **Relay-proxy mode** — only if the team actually intends to use it for a real use case (e.g. the polyglot scenario in `feature-flags-use-cases.md` Use Case 2). If nobody plans to use relay-proxy mode, this run can be skipped, but the spec must say so explicitly rather than silently omitting it and letting the embedded-mode result stand in for both.

**Requirements:**
- A load-test harness (JMH benchmark, or an external load tool like Gatling/k6 driving a small Spring Boot app wired to a real `Client`) exercising `client.getBooleanValue(...)` via the OpenFeature Java SDK against a real or Testcontainers-run GOFF relay-proxy/retriever.
- Reports p50/p99/p999 added latency and error rate per req/s tier in the sweep, for each deployment mode tested.
- Confirms (by code inspection or a targeted test, not by assumption) that the harness's own evaluation path adds no per-request I/O of its own — i.e. that whatever facade code sits between the benchmark and the OpenFeature client isn't itself the bottleneck being measured.
- At least one external reference (GOFF community/production usage at a comparable scale) gathered and cited, or an explicit written acceptance that the team is an early adopter at this scale — the checklist asks for one or the other, not both.

---

## Gate 2 — Self-serve evidence

**Checklist's proposed pass criterion:** either a non-engineer can create/flip a flag without a code deploy through an accepted workflow, or the team explicitly accepts flags-as-config/GitOps as sufficient for the foreseeable roadmap.

**This is mostly not software.** The deliverable is a short written report, not code:
- A real person who is not an engineer attempts one concrete "toggle a flag" task via GOFF's admin UI, with time-to-complete and friction points recorded.
- The same task attempted via the flags-as-config/GitOps workflow (a PR changing `flags.yaml`), same measurements.
- **Gap this slice cannot fill on its own:** an explicit statement of who actually needs to change flags without engineering involvement, and how often — this is an organizational fact this slice must gather from stakeholders, not something inferable from the codebase. Don't fabricate a plausible-sounding requirement; go ask.

---

## Non-engine blocker — `InMemoryFlagEngine` + Parameter Store caching

**Checklist's requirement:** confirm overrides are cached, not read per-evaluation, so this bridge mode doesn't itself violate ADR 0002 B3's "no I/O on the hot path" property.

**Gap — the AWS wiring this is nominally about doesn't exist yet.** Grepped the repo: no `spring-cloud-aws` dependency, no `ParameterStore`, no `@RefreshScope` anywhere. `InMemoryFlagEngine.evaluateBoolean` (`flags-noop/src/main/java/com/acme/flags/noop/InMemoryFlagEngine.java`) reads `properties.getOverrides().get(key)` — a plain in-memory `Map.get()` against a Spring `@ConfigurationProperties`-bound record. `feature-flags-facade-design.md` §4's Parameter Store path is documentation aspiration; there is currently zero code that reaches AWS.

**Recommended resolution (cheap option, structural):**
- `@ConfigurationProperties` binds once at `ApplicationContext` startup regardless of the underlying `PropertySource` (a plain properties file, environment variables, or a Parameter Store config-import) — this is standard, documented Spring behavior, not something specific to this project. Whatever supplies the values, `FlagOverridesProperties.getOverrides()` returns an already-bound in-memory `Map` with no I/O in the call path.
- Add a unit test asserting `InMemoryFlagEngine.evaluateBoolean` never triggers a re-bind/re-query of `FlagOverridesProperties` across repeated calls (e.g. a Mockito spy on the properties bean, asserting the underlying map accessor is called but nothing indicating a refresh occurs).
- Document the one real risk this argument doesn't cover: if a future change adds `@RefreshScope` (Spring Cloud's mechanism for live-reloading `@ConfigurationProperties` beans, which *would* introduce periodic I/O) to `FlagOverridesProperties`, this "no I/O on the hot path" property would need re-verifying. Flag it as a one-line warning comment on the class, not a blocker for closing this slice.

**Open question for whoever signs off ratification:** is a structural argument + unit test sufficient evidence, or does the checklist item actually require standing up real (or LocalStack-simulated) AWS Parameter Store wiring first and testing it end-to-end? This spec recommends the cheap option as sufficient, but doesn't have the authority to decide it's sufficient — that's a ratification-meeting call.

---

## Out of scope / follow-ups

- Actually wiring `spring-cloud-aws` config-import for a real deployment — only needed if the cheap structural argument above is rejected at ratification.
- Deciding the real peak req/s figure or the real self-serve requirement — this slice gathers the mechanism to produce/measure evidence; the actual numbers are inputs this slice depends on getting from stakeholders, not something it invents.

## References

- [v1 roadmap](../../feature-flags-v1-roadmap.md)
- `../../../adr/0002-ratification-checklist.md` — Gate 1, Gate 2, and the non-engine blockers this slice addresses
- `../../feature-flags-research-gaps-v2.md` Thread 5 — the relay-proxy/sidecar-overhead finding behind Gate 1's deployment-mode requirement
- `../../feature-flags-facade-design.md` §4 — the Parameter Store bridge-mode path
- `../../../flags-noop/src/main/java/com/acme/flags/noop/InMemoryFlagEngine.java`, `FlagOverridesProperties.java`
- `../../feature-flags-comparison.md`, `../../feature-flags-use-cases.md` — Use Case 2 (relay-proxy mode) referenced above
