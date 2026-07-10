# ADR 0002: Select the Feature-Flag Engine and Evolve the Facade Operating Model

> **Draft — notes toward a decision.** Not accepted. The engine pick (§Decision, Part A) needs team ratification before this moves to *Accepted*; the operating-model changes (Part B) propose amendments to ADR 0001 and should be reviewed alongside it. Citation markers `[n]` refer to the numbered reference lists: `[1]`–`[17]` in `../docs/feature-flags-research-references.md`, and the v2 extension `[18]`–`[31]` in `../docs/feature-flags-research-gaps-v2.md`.

## Status

Proposed (draft). **Engine selection (Part A) is decided — GO Feature Flag is the chosen engine.** Remaining items (Part B operating model changes, non-engine blockers from `0002-ratification-checklist.md`) still block full ratification to *Accepted*.

## Context

ADR 0001 accepted the "alongside" pattern — a thin in-house facade over a third-party OSS engine — and explicitly left three things open: **engine selection (Unleash vs. GO Feature Flag)**, facade ownership, and the rollout plan. This ADR resolves the first and, because we now have evidence 0001 did not, evolves how the facade is governed and operated.

Since 0001 we ran a literature review — 17 peer-reviewed sources, catalogued in `../docs/feature-flags-research-references.md` — against every major decision in the design set. Two things came out of it.

**What the evidence *validated* (and this ADR does not reopen):**

- The alongside pattern over both direct-SDK and full-rebuild. A mixed-method CD study concludes feature toggles "require research on better abstractions and modelling techniques for runtime variability" [5] — the facade is that abstraction. Four independent studies of vendor-abstraction layers find they add negligible request-path latency while sharply reducing per-provider development and migration cost [10][11][12][13], which is 0001's "small tax, real insurance" thesis in peer-reviewed form [8][9].
- The `expiresAfter ≈ 90 days` default in the design's example registry, which lines up with the observed "75% of toggles removed within 49 weeks" [7].

**What the evidence *challenged*** are four operating assumptions carried in ADR 0001 and `feature-flags-facade-design.md`. They are the substance of Part B. None of them touches the architecture; all of them touch how we run it.

## Decision

### Part A — Engine selection

ADR 0001's tiebreak weighted *proven-at-scale maturity* and gave Unleash the edge. The review adds a factor that tiebreak underweighted: the facade's entire justification is avoiding lock-in, and the lock-in literature names **standardized formats and protocols** as the single most effective mitigation [8]. GO Feature Flag is built on **OpenFeature** (an industry-standard evaluation API); Unleash speaks a proprietary SDK. Choosing the standards-native engine means the *adapter itself* writes to a standard — so for a future third engine that is also OpenFeature-compliant, **adapter code shrinks rather than disappears**: the swap is far closer to a configuration change than a rewrite, but `GoffFlagEngine`'s context-translation and typed-dispatch logic would still need re-validating against the new provider's *actual* OpenFeature implementation, not just its compliance label. Open-standard compliance is necessary but not sufficient for full cross-vendor interoperability — vendors often lack commercial motivation to invest beyond nominal compliance [19]. It still compounds the very insurance the facade exists to buy, just less absolutely than "no new adapter at all" would imply.

**Decision: GO Feature Flag is the chosen engine.** Unleash remains the documented fallback if GOFF fails to meet scale or self-serve requirements during ratification evidence gathering (see `0002-ratification-checklist.md` Gates 1 and 2).

### Part B — Evolution of the facade operating model (amends ADR 0001)

**B1. Automated flag-lifecycle governance, from day one.** `feature-flags-facade-design.md` §6 defers enforcement to "review (not tooling, initially)" and presents the stale-flag job as illustrative. The evidence is uniform that manual cleanup discipline fails: removals lag additions (35% in Kubernetes, 13% in GitLab), so inventories grow without bound and some toggles become permanent [6]; the practitioner survey found *every* team uses dedicated tooling, not review, to manage this [2]; and stale toggles are the canonical accumulation of technical debt [1]. We therefore commit, as shipped deliverables in the first facade release:
- a stale-flag detection job (the design's `@Scheduled` sketch, promoted from example to deliverable) that emits [6]'s metric set — removal-lag ratio, inventory-growth rate, lifespan percentiles — not just a binary past-expiry check;
- a CI gate on registry entries: `owner`, `ticket`, and `expiresAfter` present (or an explicit `permanent` marker), enforced mechanically rather than by reviewer memory.

**B2. Cross-service interaction visibility.** `feature-flags-facade-design.md` §5 rejects a global enum for per-service registries — correct for team independence — and delegates org-wide visibility to the engine's admin UI. But that UI shows flag *states*, not code-level *interactions* (one flag gating another, nested conditionals across services). The evidence says that is exactly where toggle complexity concentrates: ~7% of toggles interact with each other and interactions grow ~22% year over year [4], and the Knight Capital bankruptcy — the reference flag disaster — was an interaction/repurposing failure, not a single-flag bug [2]. We keep decentralized registries and add a **fleet-level static aggregation** across all `FlagKey` registries that surfaces keys referenced in combination. (Scope check: if this grows past a scan into its own subsystem, split it into ADR 0003 rather than expanding this one.)

**B3. A resilience contract for context resolution.** The docs contradict themselves on where targeting traits come from: `feature-flags-facade-sketch.md` §3 reads them from an in-memory request-scoped bean (no I/O), while `feature-flags-use-cases.md` Use Case 1 shows a synchronous call to the Entitlements service on the evaluation path — yet the cross-tool summary asserts the facade "adds no I/O of its own." We resolve this explicitly:
- **Default:** resolve traits off the hot path / from already-request-scoped data, keeping the "no I/O" property genuinely true.
- **If a live entitlements call is unavoidable** for some flags, it must carry timeout + circuit breaker + a declared fallback trait, per the resilience literature — a bare `try/catch → default` (the only failure handling in `feature-flags-facade-sketch.md` §5, and it wraps only the engine call) is insufficient for a synchronous service dependency [14][16].

Either way, reconcile the "adds no I/O" claim in `feature-flags-use-cases.md` with whichever path is chosen.

**B4. Reframe the facade's cost as lifecycle, not setup.** ADR 0001 and `comparison.md` score the facade as a one-point *setup* tax. The dominant long-run cost of any flag system is toggle *technical debt* [1], which the perf/setup/scalability axis cannot see and which lands on whoever owns the registry. This is a framing correction, not a new decision — and it is precisely why B1 and B2 are committed up front rather than deferred.

## Consequences

### Positive
- Implementation can start: the engine is chosen (pending ratification), unblocking the `flags-engine-*` adapter and the `1.0.0` cut described in `feature-flags-facade-design.md` §8.
- Picking the OpenFeature-native engine maximizes the lock-in insurance the facade was built for — the adapter writes to a standard, so a future engine swap is far closer to a configuration change than a rewrite (adapter code shrinks rather than vanishing — OpenFeature compliance is necessary but not sufficient [19]).
- Governance automation (B1) heads off the inventory-growth failure mode every longitudinal study documents [1][6][7], instead of discovering it after the fleet has hundreds of stale flags.
- Interaction visibility (B2) closes the highest-severity risk class in the corpus [2][4].

### Negative / risks
- B1 and B2 are additional build scope in the first release. They are small relative to the debt they prevent, but they are not free, and they compete with shipping the happy path.
- Choosing GOFF accepts its smaller-community / thinner-UI risk. Mitigation is the facade itself plus the engine-swap mechanic from ADR 0001 — the exit ramp is cheap by construction.
- The B3 resilience contract adds a circuit breaker / caching concern to the facade core if the live-entitlements path is taken. The alternative, though, is a hidden per-request dependency on the evaluation hot path — worse.

## Guardrails

Carry forward ADR 0001's core rule unchanged: **the facade may consume the engine's state but must never own a second copy of it.**

One amendment for the new tooling: the B1 governance job and the B2 interaction scan are **read-only** over the registries and engine state. They observe and notify; they do not delete, toggle, or persist an authoritative copy of anything. The stale-flag job files a notification — it never auto-removes a flag. This keeps B1/B2 inside 0001's guardrail rather than quietly becoming a second control plane.

## Follow-ups / open questions

- ~~**Ratify the engine (GOFF recommended).**~~ **Engine is decided: GO Feature Flag.** Remaining ratification work is evidence gathering (Gates 1 and 2 in `0002-ratification-checklist.md`) to confirm the decision against actual scale and self-serve requirements.
- **Facade ownership** — still open from ADR 0001: which team owns the library, its versioning, and the on-call rotation for the evaluation path (now also owns the B1 governance job).
- **Interaction detection scope (B2)** — split into **ADR 0003 (draft)** (`0003-cross-service-flag-interaction-scan.md`), which decides it stays a build-time static scan (Tier 1) behind an escalation trigger to a runtime subsystem (Tier 2 → future ADR); confirm that scope at ratification.
- **`InMemoryFlagEngine` + AWS Parameter Store path** (`feature-flags-facade-design.md` §4) — confirm overrides are cached, not read per-evaluation, so this bridge mode doesn't itself violate the B3 "no I/O on the hot path" property.
- **Relay-proxy latency benchmark (GOFF deployment mode).** `feature-flags-comparison.md` and `feature-flags-use-cases.md` (Use Cases 1–2) recommend GOFF's relay-proxy / sidecar deployment mode without quantifying its latency cost. A relay proxy is structurally a network hop per evaluation, unlike embedded/in-process mode; measured sidecar overhead runs up to **269% higher latency** (and up to 163% more vCPU) versus in-process handling [31]. Benchmark the relay-proxy path's added p99 latency against embedded mode as part of Gate 1's peak-load evidence, and record it so the deployment-mode recommendation is quantified rather than assumed equivalent.
- **See `../docs/feature-flags-v1-roadmap.md`** for how ratification and these follow-ups sequence against the rest of v1 (Slices A–E), including the open question of whether B1/B2 can ship ahead of the bundled `1.0.0` cut this ADR's Consequences section implies.

## References

- `0001-adopt-in-house-facade-alongside-flag-engine.md` — the decision this evolves
- `../docs/feature-flags-research-references.md` — the 17 numbered sources and their mapping to each artifact
- `../docs/feature-flags-research-gaps-v2.md` — follow-up research pass on gaps/alternatives/enhancements, including a live code-review finding on `FlagContext.current()` (Thread 4) and `DefaultFeatureFlags`'s exception handling (Thread 6)
- `../docs/feature-flags-facade-design.md` — §5 (registries), §6 (governance), §4 (bridge engines) amended by Part B
- `../docs/feature-flags-facade-sketch.md` — §3/§5 (context resolution, failure policy) amended by B3
- `../docs/feature-flags-comparison.md`, `../docs/feature-flags-use-cases.md` — the "facade tax" framing (B4) and the "no I/O" claim (B3)
