# ADR 0002 Ratification Checklist — Engine Selection (Part A)

> **Purpose.** Complete this checklist before changing ADR 0002's Status from *Proposed (draft)* to *Accepted*. It turns Part A's engine recommendation and its two flip conditions into explicit go/no-go gates, and gathers the remaining items that ADR 0001/0002 say must be settled before implementation starts. Citation markers `[n]` refer to the numbered list in `../docs/feature-flags-research-references.md`.

> **Engine Decision Status:** GO Feature Flag is the chosen engine (updated 2025-01-XX). Gates 1 and 2 below are now evidence-gathering activities to **confirm** this decision, not decision points. Unleash remains the documented fallback if evidence shows GOFF cannot meet requirements.

## Decision rule

**GO Feature Flag is the chosen engine.** Gates 1 and 2 below are evidence-gathering activities to **confirm** this decision against actual requirements. If evidence shows GOFF cannot meet the pass criteria, the decision flips to Unleash as the fallback.

GOFF was selected because it is OpenFeature-native (see §"Why GOFF by default"). The two gates below are the only findings the research review could not settle for us; they are business/ops judgments about our real load and our real users. ADR 0002 is not *Accepted* until **both** engine gates are resolved (PASS, or FAIL → Unleash) **and** every item in "Also required before Accepted" is checked or consciously waived.

Each gate records: the proposed pass criterion, the evidence to gather, a result, and what a failure means.

> **On the proposed thresholds.** The numbers below are **straw-man targets to react to**, not measurements of our system — no real load figures exist in this repo yet, and Part A is explicit that the call must be made "against our actual peak load and self-serve needs." Treat every *PROPOSED* value as a starting point to confirm or replace at the ratification meeting.

---

## Gate 1 — Peak-load / scale

ADR 0001's tiebreak favored Unleash on *proven-at-scale maturity*; GOFF has a smaller community that "has not yet demonstrated" behavior at our peak (Part A). This gate discharges that specific doubt.

**Proposed pass criterion (PROPOSED — confirm or replace):**
- A single GOFF relay/evaluation instance sustains the service's **projected peak flag-evaluation rate** with **p99 added evaluation latency ≤ ~5 ms** on the hot path, consistent with the vendor-abstraction latency findings [10][11][12][13].
- Behavior past that peak is **safe** (bounded latency / documented backpressure), not a cliff.
- The result holds with the facade's caching model in place — evaluations served from already-request-scoped / cached state, not a per-evaluation network call (ties to B3 and to the Parameter Store item below).

**Evidence to gather:**
- [ ] A load test against a GOFF instance at our projected peak req/s, reporting p99/p999 added latency and error rate.
- [ ] At least one external reference (community or production) running GOFF at a scale ≥ our peak, or an explicit acceptance that we are an early adopter at this scale.
- [ ] Confirmation the facade's evaluation path adds no per-request I/O of its own under this load.

**Result:** ☐ PASS  ☐ FAIL
**If FAIL → adopt Unleash** (proven-at-scale is the hard requirement here). Record the measured numbers regardless, so the decision is auditable.

---

## Gate 2 — Self-serve for non-engineers

Part A flags GOFF's admin UI as thin: its flags-as-config model is GitOps-friendly but not PM-friendly, whereas Unleash offers first-class self-serve flag management.

**Proposed pass criterion (PROPOSED — confirm or replace):**
- Either: a non-engineer (PM/ops) can **create and flip a flag without a code deploy** through an accepted workflow (GOFF admin UI, or a self-serve layer we accept the cost of building); **or** the team explicitly accepts **flags-as-config via GitOps** (PR-per-change) as sufficient self-serve for the foreseeable roadmap.

**Evidence to gather:**
- [ ] A spike exercising GOFF's admin UI against one real "PM toggles a flag" task, versus the same task via the flags-as-config workflow. — *Not run: no live GOFF UI and no non-engineer to recruit in the sandbox. Deferred to the mandatory re-run trigger at first real-consumer onboarding (see the Gate 2 runbook).*
- [x] An explicit statement of who needs to change flags without engineering involvement, and how often — the actual requirement this gate is testing. — *Recorded truthfully in the Gate 2 runbook: no non-engineer consumer exists yet (single-committer sandbox); real demand is unknown until a product team adopts the facade.*

**Result:** ☑ **PASS (provisional) — via path (b), 2026-07-10.** The team explicitly accepts flags-as-config / GitOps (PR-per-change) as sufficient self-serve, given no non-engineer consumer exists yet and the design is GitOps-native. Provisional because the usability spike was **not** run; the mandatory re-run at first real-consumer onboarding (Gate 2 runbook) can still flip this to FAIL → Unleash.
**If FAIL → adopt Unleash** (first-class self-serve is the hard requirement here).

---

## Why GOFF by default (tie-break rationale)

Kept in front of the meeting so the default is understood, not assumed:

- The facade's entire justification is avoiding lock-in, and the lock-in literature names **standardized formats and protocols** as the single most effective mitigation [8].
- GOFF is built on **OpenFeature** (an industry-standard evaluation API); Unleash speaks a proprietary SDK.
- Choosing the standards-native engine means the **adapter itself writes to a standard** — so a future third engine that is also OpenFeature-compliant needs *far less adapter work*, closer to a configuration change than a rewrite (adapter code shrinks rather than disappears; context-translation still needs re-validating per provider, since OpenFeature compliance is necessary but not sufficient — see ADR 0002 Part A). That compounds the very insurance the facade exists to buy.

The research review tilts the *criteria* toward GOFF; Gates 1 and 2 are the two things it cannot decide for us.

---

## Also required before 'Accepted' (non-engine blockers)

These are open items from ADR 0001/0002 that gate implementation start; the engine pick alone does not make ADR 0002 ratifiable.

- [x] **Facade ownership — consciously WAIVED for the sandbox (2026-07-10).** Naming a real owning team is impossible while this is a single-committer sandbox; assigning one would be fiction, not a decision. Waived per this checklist's own "checked **or consciously waived**" rule, conditioned on recording the *intended owner profile* below so a real assignment has defined criteria at go-live. Converts to a real assignment when the project reaches a live org context or first go-live — kept deliberately separate from ADR 0001's "which service integrates first" follow-up (the first integrator is *not* the default owner, to avoid shaping the API around one consumer).

  **Intended owner profile** — a platform/infra-style team, neutral across consuming services, that can carry all five as one charter (or records the split at assignment time): (1) the `flags-*` library + `FlagEngine`/`FlagContextResolver` SPI + public API surface; (2) versioning / SemVer discipline (breaking changes have fleet-wide blast radius — ADR 0001 Consequences); (3) on-call for the runtime evaluation hot path; (4) the B1 stale-flag governance job (Slice D); (5) the cross-service interaction scan (Slice E, ADR 0003 Tier 1).
- [x] **B2 interaction-scan scope confirmed (2026-07-10).** Ratified **ADR 0003** (`0003-cross-service-flag-interaction-scan.md`) → *Accepted*: interaction visibility is a build-time static **advisory** scan (Tier 1) with a defined escalation trigger to a runtime subsystem (Tier 2 → future ADR 0004). Tier-1 scope and read-only/advisory contract confirmed; the cross-repo aggregation mechanism and the Tier-2 count threshold are deferred to first real-consumer integration.
- [x] **InMemoryFlagEngine + AWS Parameter Store path confirmed cached (Blocker 2 Path A).** Verified: overrides bind once at startup and evaluation is a plain `Map.get()` — no per-evaluation I/O, so the bridge mode does not violate B3's "no I/O on the hot path" property (`../docs/feature-flags-facade-design.md` §4). Evidence: `InMemoryFlagEngineNoHotPathIoTest` + the structural argument in `../BLOCKER_2_RESOLUTION.md`. Path B (LocalStack real-AWS test) is now unblocked by ADR 0003 acceptance but optional — Path A already satisfies this blocker.

---

## Sign-off

| Field | Value |
|---|---|
| Engine ratified | ☐ GO Feature Flag (chosen, pending evidence confirmation)   ☐ Unleash (fallback if Gates 1/2 fail) |
| Gate 1 (peak-load) | ☐ PASS  ☐ FAIL |
| Gate 2 (self-serve) | ☑ PASS (provisional — path (b), GitOps accepted; re-run at first real-consumer)  ☐ FAIL |
| Non-engine blockers all checked/waived | ☑ Yes (ownership waived; B2 confirmed; Parameter Store confirmed) |
| Ratified by | __________________________ |
| Date | __________________________ |

**On sign-off:**
1. Update ADR 0002's **Status** from *Proposed (draft)* to *Accepted* (note the ratified engine and this checklist).
2. If the B2 blocker was satisfied by confirming ADR 0003's scope, update **ADR 0003**'s Status from *Proposed (draft)* to *Accepted* as well.
3. Unblock the `flags-engine-*` adapter work against the existing `flags-contract-test` conformance suite, toward the `1.0.0` cut described in `../docs/feature-flags-facade-design.md` §8.

## References

- `0002-select-flag-engine-and-evolve-facade-operating-model.md` — the ADR this checklist ratifies (Part A, engine selection).
- `0001-adopt-in-house-facade-alongside-flag-engine.md` — original "alongside" decision; source of the open facade-ownership item.
- `../docs/feature-flags-research-references.md` — the numbered sources behind `[n]` markers.
- `../docs/feature-flags-facade-design.md` — §4 (bridge engines / Parameter Store), §8 (release plan).
