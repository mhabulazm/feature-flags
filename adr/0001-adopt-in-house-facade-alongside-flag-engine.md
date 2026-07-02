# ADR 0001: Adopt an In-House Facade Alongside a Third-Party Feature-Flag Engine

## Status

Accepted — engine selection (Unleash vs. GO Feature Flag) is an open follow-up, not blocking this decision.

## Context

We evaluated open-source feature-flag options for our Java / Spring Boot / PostgreSQL / AWS microservices stack: five standalone platforms (Unleash, Flagsmith, GrowthBook, FF4J, GO Feature Flag) and two forms of an in-house build (*alone* — building the evaluation engine, storage, and propagation ourselves; *alongside* — a thin custom layer wired on top of one of the standalone platforms). Full detail is in `../docs/feature-flags-comparison.md`, `../docs/feature-flags-use-cases.md`, and `../docs/feature-flags-facade-sketch.md`; synthesis in `../docs/feature-flags-executive-summary.md`.

Scoring performance, setup complexity, and scalability (1–5 each, 15 max) across all seven options produced a top tier of four, tied or near-tied:

| Option | Score /15 |
|---|---|
| Unleash (standalone) | 13 |
| GO Feature Flag (standalone) | 13 |
| In-house, alongside Unleash | 12 |
| In-house, alongside GOFF | 12 |

The one-point gap between the standalone tools and the "alongside" pattern is the cost of a facade we build and maintain ourselves. That axis alone doesn't capture what the facade buys: a typed flag API, targeting resolved from our own entitlements data instead of duplicated as vendor-side segments, a centrally-enforced fail-open/fail-closed policy per flag applied uniformly fleet-wide, and insurance against being locked into one vendor's SDK.

A full in-house rebuild (*alone*, full-parity scope) was ruled out — it ties Flagsmith for the lowest score (8/15), but with a categorically worse cost profile: Flagsmith's weak point is a config flag away from fixed, while a full rebuild is open-ended engineering cost (see the Wayfair example cited in the comparison doc, ~$3M+/year on a homegrown system at that scope).

## Decision

We will adopt the **"alongside" pattern**: a thin in-house facade sitting in front of a third-party open-source feature-flag engine, rather than (a) calling a vendor SDK directly from application code, or (b) building the evaluation engine, storage, and propagation ourselves.

The facade:
- Exposes a typed `FeatureFlags` API (enum-based flag keys, not strings).
- Auto-resolves evaluation context (tenant, user, plan traits) from our existing domain data instead of re-declaring it as vendor-side segments.
- Enforces a per-flag `FailurePolicy` (fail-open/fail-closed) centrally, applied identically across every service.
- Delegates actual evaluation to a swappable `FlagEngine` SPI, so the underlying vendor is an implementation detail, not something application code depends on.

The underlying engine (Unleash or GO Feature Flag) is **not decided by this ADR** — see Follow-ups. The SPI is specifically designed so this is a configuration change, not a rewrite.

## Consequences

### Positive
- Application call sites never depend on a vendor SDK directly — the engine can be swapped or a second one added as fallback without touching business logic.
- Targeting, failure behavior, and governance are enforced consistently fleet-wide instead of being decided ad hoc per team/service.
- Testing gets a first-class fake (`FakeFeatureFlags`) with no vendor-SDK mocking required.

### Negative / risks
- The facade is a new internal library with an ongoing maintenance owner — this is real, recurring cost, not a one-time setup.
- Without discipline, the facade can drift into re-implementing the engine (its own storage, its own rollout math, a second admin UI) — at which point it inherits the cost profile of the worst-scoring option evaluated (full-parity, alone). See Guardrails.
- Every application service takes a compile-time dependency on this library; a breaking change to the facade's API has fleet-wide blast radius and needs a deliberate versioning/rollout plan.

## Guardrails

The facade **may consume the engine's state but must never own a second copy of it**. Concretely, out of scope for the facade, permanently:

- Independent flag storage (a local table that's an alternate source of truth).
- Independent rollout/bucketing math (percentage rollouts, consistent hashing) — this stays in the engine.
- A second admin/control-plane UI for toggling flags.
- Approval workflows, change-request review, or other governance features better solved by the engine's Enterprise tier if we need them.

A PR that adds any of the above to the facade should be treated as a signal to stop and re-evaluate, not merged as a routine change. Full reasoning: `../docs/feature-flags-facade-sketch.md`.

## Follow-ups / open questions

- **Engine selection (Unleash vs. GO Feature Flag).** Both score identically on performance/setup/scalability; the decision is maturity + richer admin UI (Unleash) vs. minimal ops footprint (GOFF) — see `../docs/feature-flags-comparison.md` for the tiebreak reasoning. Needs a follow-up ADR before implementation starts.
- **Facade ownership.** Which team owns the shared library, its versioning policy, and the on-call rotation for the flag-evaluation path.
- **Rollout plan.** Which service integrates first, and how the `Feature` registry and `FailurePolicy` defaults get reviewed before go-live.

## References

- `../docs/feature-flags-executive-summary.md`
- `../docs/feature-flags-comparison.md`
- `../docs/feature-flags-use-cases.md`
- `../docs/feature-flags-facade-sketch.md`
- `../docs/feature-flags-facade-design.md` — finalized interface contract, module layout, versioning, and testing strategy (written after this ADR, ahead of engine selection)
