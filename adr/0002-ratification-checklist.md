# ADR 0002 Ratification Checklist — Engine Selection (Part A)

> **Purpose.** Complete this checklist before changing ADR 0002's Status from *Proposed (draft)* to *Accepted*. It turns Part A's engine recommendation and its two flip conditions into explicit go/no-go gates, and gathers the remaining items that ADR 0001/0002 say must be settled before implementation starts. Citation markers `[n]` refer to the numbered list in `../docs/feature-flags-research-references.md`.

## Decision rule

**Adopt GO Feature Flag (GOFF) as the engine unless Gate 1 or Gate 2 fails — in which case fall back to Unleash.**

GOFF is the *recommended default* because it is OpenFeature-native (see §"Why GOFF by default"). The two gates below are the only findings the research review could not settle for us; they are business/ops judgments about our real load and our real users. ADR 0002 is not *Accepted* until **both** engine gates are resolved (PASS, or FAIL → Unleash) **and** every item in "Also required before Accepted" is checked or consciously waived.

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
- [ ] A spike exercising GOFF's admin UI against one real "PM toggles a flag" task, versus the same task via the flags-as-config workflow.
- [ ] An explicit statement of who needs to change flags without engineering involvement, and how often — the actual requirement this gate is testing.

**Result:** ☐ PASS  ☐ FAIL
**If FAIL → adopt Unleash** (first-class self-serve is the hard requirement here).

---

## Why GOFF by default (tie-break rationale)

Kept in front of the meeting so the default is understood, not assumed:

- The facade's entire justification is avoiding lock-in, and the lock-in literature names **standardized formats and protocols** as the single most effective mitigation [8].
- GOFF is built on **OpenFeature** (an industry-standard evaluation API); Unleash speaks a proprietary SDK.
- Choosing the standards-native engine means the **adapter itself writes to a standard** — so a future third engine that is also OpenFeature-compliant needs *no new adapter*, only a configuration change. That compounds the very insurance the facade exists to buy.

The research review tilts the *criteria* toward GOFF; Gates 1 and 2 are the two things it cannot decide for us.

---

## Also required before 'Accepted' (non-engine blockers)

These are open items from ADR 0001/0002 that gate implementation start; the engine pick alone does not make ADR 0002 ratifiable.

- [ ] **Facade ownership assigned.** Name the team that owns the library, its versioning, the on-call rotation for the evaluation path, **and** the B1 stale-flag governance job. (Open since ADR 0001; the B1 job now rides on this owner.)
- [ ] **B2 interaction-scan scope confirmed.** Ratify **ADR 0003 (draft)** (`0003-cross-service-flag-interaction-scan.md`), which decides interaction visibility is a build-time static scan (Tier 1) with a defined escalation trigger to a runtime subsystem (Tier 2 → future ADR). Confirm its Tier-1 scope and read-only/advisory contract.
- [ ] **InMemoryFlagEngine + AWS Parameter Store path confirmed cached.** Verify overrides are cached, not read per-evaluation, so the bridge mode does not itself violate B3's "no I/O on the hot path" property (`../docs/feature-flags-facade-design.md` §4).

---

## Sign-off

| Field | Value |
|---|---|
| Engine ratified | ☐ GO Feature Flag   ☐ Unleash |
| Gate 1 (peak-load) | ☐ PASS  ☐ FAIL |
| Gate 2 (self-serve) | ☐ PASS  ☐ FAIL |
| Non-engine blockers all checked/waived | ☐ Yes |
| Ratified by | __________________________ |
| Date | __________________________ |

**On sign-off:**
1. Update ADR 0002's **Status** from *Proposed (draft)* to *Accepted* (note the ratified engine and this checklist).
2. Unblock the `flags-engine-*` adapter work against the existing `flags-contract-test` conformance suite, toward the `1.0.0` cut described in `../docs/feature-flags-facade-design.md` §8.

## References

- `0002-select-flag-engine-and-evolve-facade-operating-model.md` — the ADR this checklist ratifies (Part A, engine selection).
- `0001-adopt-in-house-facade-alongside-flag-engine.md` — original "alongside" decision; source of the open facade-ownership item.
- `../docs/feature-flags-research-references.md` — the numbered sources behind `[n]` markers.
- `../docs/feature-flags-facade-design.md` — §4 (bridge engines / Parameter Store), §8 (release plan).
