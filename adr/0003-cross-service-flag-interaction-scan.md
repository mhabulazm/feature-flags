# ADR 0003: Cross-Service Flag Interaction Visibility via a Build-Time Static Scan

> **Draft — notes toward a decision.** Not accepted. This ADR takes up the scope-check ADR 0002 Part B (B2) deferred to it: whether cross-service flag-interaction visibility stays a scan or becomes its own subsystem. It needs team ratification before moving to *Accepted*, and resolves the B2 interaction-scan-scope blocker in `0002-ratification-checklist.md`. Citation markers `[n]` refer to the numbered list in `../docs/feature-flags-research-references.md`.

## Status

Proposed (draft). Implements and bounds ADR 0002 Part B item **B2** (cross-service interaction visibility). Does not block ADR 0002 Part A (engine selection); it discharges the separate B2-scope blocker the ratification checklist lists before ADR 0002 is *Accepted*.

## Context

`feature-flags-facade-design.md` §5 deliberately rejects a global flag enum in favor of **per-service registries** — each service defines its own `FlagKey` enum, keys namespaced by service (`checkout.new-checkout-flow`, `billing.rate-limit-override`). This is correct for team independence, and org-wide *state* visibility is intentionally delegated to the chosen engine's admin UI rather than rebuilt at the facade (principle 1: never own a second copy of state).

But that admin UI shows flag **states**, not code-level **interactions**: one flag gating another, a flag's branch nesting a second flag's check, or a service reading a flag key that another service owns. The evidence puts exactly this in the danger zone:

- Toggles interact — **~7% interact with each other, and interactions grow ~22% year over year** — which breaks the "just a boolean" mental model and motivates a modeled, inspectable registry over scattered flags [4].
- The **Knight Capital** bankruptcy — the standing flag-disaster anecdote — was an old toggle **erroneously repurposed**: an interaction/lifecycle failure, not a single-flag bug [2].

ADR 0002 B2 committed to keeping decentralized registries and adding a **fleet-level static aggregation across all `FlagKey` registries that surfaces keys referenced in combination**, with an explicit scope-check: *"if this grows past a scan into its own subsystem, split it into ADR 0003 rather than expanding this one."* This ADR is that split. It decides the shape of the capability now, and draws the line past which it would become a separate subsystem.

## Decision

Adopt a **two-tier** approach. Ship the scan now; hold the subsystem behind an explicit trigger.

### Tier 1 — Build-time static interaction scan (adopt now)

A fleet-level, build-time scan that:

- **Aggregates** every service's `FlagKey` registry (all enums implementing the shared `flags-api` `FlagKey` interface) into one inventory of known keys and their owning service.
- **Scans source** for flag-key references and surfaces keys **referenced in combination**, in three categories:
  - **Co-reference** — two or more flag keys evaluated in the same method or conditional expression.
  - **Nesting** — a flag's enabled/disabled branch that itself contains another flag's evaluation.
  - **Cross-service** — a service referencing a flag key from another service's namespace.
- **Emits an interaction report** (the set of interacting key pairs/groups, with the referencing site). It is **read-only** and **advisory**: it warns and notifies; it does **not** block merges and does **not** mutate, toggle, or persist an authoritative copy of anything.

Detection stays at the level of *keys referenced together*; this ADR specifies the categories and the read-only/advisory contract, not a particular parser or output format — those are implementation choices for whoever owns the facade (the scan runs alongside the B1 governance job under the same owner).

### Tier 2 — Runtime interaction subsystem (deferred, behind a trigger)

A heavier capability — dynamic interaction detection, a live interaction graph, dashboards — is **not** built now. It is warranted only if an **escalation trigger** fires:

- an incident is traced to an interaction the static scan structurally **could not see**; or
- interactions are materially **dynamic / config-driven** (composed at runtime, not visible to static analysis); or
- the interaction count crosses a threshold the team sets at ratification, past which a report is no longer a usable review artifact.

If a trigger fires, Tier 2 gets **its own ADR (0004)** rather than an expansion of this one — the same scope discipline ADR 0002 applied to this ADR.

## Consequences

### Positive
- Closes the highest-severity risk class in the corpus — interaction and repurposing failures [2][4] — at the cost of a CI scan rather than a subsystem.
- Stays strictly inside ADR 0002's read-only guardrail: the scan observes and notifies, so it never becomes a second control plane over flag state.
- Keeps registries decentralized and teams independent; the scan is additive and touches no service's ownership of its own flags.
- The explicit Tier-2 trigger means the org builds the expensive thing only on evidence, not on speculation.

### Negative / risks
- A static scan is **blind to dynamic / config-driven interactions** — flags composed at runtime rather than referenced in source. This is a real gap, and it is precisely the first Tier-2 escalation trigger rather than a reason to reject Tier 1.
- Fleet-level aggregation needs **read access to every service repository** (or a monorepo view). The aggregation mechanism is an open question below.
- The co-reference heuristic can **false-positive** — two keys in one method are not always a genuine interaction. Advisory-only output keeps a false positive cheap (a review note, not a blocked merge).

## Guardrails

Carry forward ADR 0002's guardrail unchanged: the scan is **read-only** over the registries and engine state — it observes and notifies; it does not delete, toggle, or persist an authoritative copy of anything. It files findings; it does not gate merges. This deliberately makes it **advisory**, distinct from B1's metadata **CI gate** (`owner`/`ticket`/`expiresAfter` presence), which does block. Keeping the interaction scan advisory holds it inside the read-only rule rather than letting it quietly become a second control plane.

## Follow-ups / open questions

- **Cross-repo aggregation mechanism** — how a fleet-level scan discovers and reads every service's `FlagKey` registry (monorepo view, a published registry artifact per service, or a CI aggregation step). Decide before implementation.
- **Interaction heuristic precision** — the exact rule for "referenced in combination" (AST co-reference scope, how deep nesting is followed), and the acceptable false-positive rate for an advisory report.
- **Graduation criteria** — whether Tier 1 ever moves from advisory to blocking for a subset of interactions (e.g. cross-service references to a `FAIL_CLOSED` flag), and the Tier-2 count threshold.
- **Ownership** — the facade owner (still an open item in `0002-ratification-checklist.md`) runs this scan alongside the B1 stale-flag job.

## References

- `0002-select-flag-engine-and-evolve-facade-operating-model.md` — Part B item B2, which this ADR implements and bounds.
- `0002-ratification-checklist.md` — lists the B2 interaction-scan-scope blocker this ADR resolves.
- `0001-adopt-in-house-facade-alongside-flag-engine.md` — the "never own a second copy of state" guardrail this inherits.
- `../docs/feature-flags-facade-design.md` — §5 (per-service registries) the scan aggregates over.
- `../docs/feature-flags-research-references.md` — sources behind `[n]` markers; [2] (practices catalog / Knight Capital) and [4] (Tërnava et al., *On the Interaction of Feature Toggles*).
