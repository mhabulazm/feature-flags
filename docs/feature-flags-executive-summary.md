# Feature Flag Tooling — Executive Summary

**Bottom line:** Adopt **Unleash** or **GO Feature Flag** as the core evaluation engine — they're statistically tied on performance, setup, and scalability, so the choice comes down to maturity/UI (Unleash) vs. minimal ops footprint (GOFF). Only add a thin in-house layer on top if there's a real recurring customization need; never rebuild the engine itself.

---

## Context & scope

Evaluated open-source feature-flag options for a Java / Spring Boot / PostgreSQL / AWS microservices stack, plus whether building in-house is competitive with adopting one of the shortlisted tools. Full detail lives in three companion documents (linked at the bottom); this page is the synthesis.

## What was evaluated

- **Five OSS platforms:** Unleash, Flagsmith, GrowthBook, FF4J, GO Feature Flag (GOFF).
- **The in-house alternative, in two forms:** *alone* (build the evaluation engine, storage, and propagation yourself) and *alongside* (a thin custom API layer wired on top of one of the five tools, which does the hard distributed-systems work for you).
- **Criteria:** license and ops footprint, request-path latency cost, scalability, targeting/rollout depth, experimentation support, and admin UI — with performance, setup complexity, and scalability formally scored 1–5 each across all nine options for a ranked shortlist.

## Ranked outcome

Four options tie at the top of the combined performance + setup + scalability score:

| Rank | Option | Score /15 | What it trades off |
|---|---|---|---|
| 1 (tie) | **Unleash** | 13 | Most mature, proven at scale; heaviest of the four to run (Node server + Postgres) |
| 1 (tie) | **GO Feature Flag** | 13 | Lightest footprint (no mandatory database, single binary); youngest, smallest community |
| 3 (tie) | **In-house, alongside Unleash** | 12 | Unleash's profile + org-specific customization, at the cost of a facade to build and maintain |
| 3 (tie) | **In-house, alongside GOFF** | 12 | GOFF's profile + the same customization benefit |
| 6 | GrowthBook | 10 | Best experimentation engine, but needs MongoDB + a warehouse integration |
| 7 | FF4J | 9 | Zero new infra (embedded library), but no built-in fleet-wide consistency |
| 8 (tie) | Flagsmith | 8 | Flags + remote config combined, but defaults to a per-request network call |
| 8 (tie) | In-house, alone (full-parity scope) | 8 | Same score as Flagsmith, but a categorically worse trade (see below) |

*(In-house, alone at MVP scope also scores 12 but is excluded from the "winners" — see findings.)*

## Key findings

- **Two tools have a hidden default-configuration trap.** Flagsmith's server-side SDK makes a live network call per flag check unless you explicitly enable local-evaluation mode; FF4J's default JDBC store hits Postgres per check unless you wrap it in a caching decorator. Both *can* reach the same near-zero-cost tier as Unleash/GrowthBook/GOFF — but only with deliberate reconfiguration, and the cost only shows up once under real load.
- **FF4J has no central propagation mechanism.** Each service embedding it manages its own cache independently, so flag-state consistency across a fleet is a manual discipline the tool doesn't enforce — a real risk once more than a couple of services share a flag table.
- **GOFF's fast-path default is recent.** In-process (Tier-1) evaluation only became the default for its Java provider as of v1.0.0 in 2026 — pin a current provider version rather than assuming older docs still apply.
- **Pure in-house is a trap at both ends of scope.** An MVP-scope build looks deceptively competitive (12/15) but is the version teams build first and rarely stay at; a full-parity build (targeting, audit, admin UI, propagation) ties Flagsmith for last (8/15) with a strictly worse cost profile — Flagsmith's weak point is a config flag away from fixed, while in-house's is open-ended engineering time. This mirrors publicly reported build-vs-buy outcomes (e.g., Wayfair's homegrown system reportedly running $3M+/year once it reached that scope).
- **The "alongside" pattern is where in-house actually pays off.** A thin facade on top of Unleash or GOFF captures the real value of building in-house — a typed API, tenant-aware targeting resolved from your own entitlements data, centralized fail-open/fail-closed policy, vendor-swap insurance — without inheriting the cost of rebuilding the engine. The condition is discipline: the facade may consume the engine's state but must never own a second copy of it (no independent storage, no independent rollout math, no second admin UI) — the moment it does, it has become the worst-scoring "alone, full-parity" option.

## Recommendation

1. **Adopt Unleash or GO Feature Flag as the core engine.** The decision is maturity/richer admin UI vs. minimal ops footprint — not a performance or scalability difference; they're tied on both.
2. **Only build the in-house facade layer if there's a concrete recurring need it serves** — tenant-aware targeting off existing entitlements data, fleet-wide failure-policy enforcement, governance (stale-flag detection, naming/ticket conventions), or vendor-swap insurance. Otherwise, adopt the engine directly and skip the facade.
3. **If built, hold the facade to its guardrail:** consume state, never own it. Any PR adding persistent storage or independent rollout logic to the facade is a signal to stop and ask whether the vendor's Enterprise tier already solves it before building it in-house.
4. **Avoid Flagsmith and FF4J for latency-sensitive hot paths at default configuration** — both are viable, but only with explicit reconfiguration (local-eval mode; caching decorator) that's easy to miss.
5. **Rule out a ground-up in-house rebuild.** Every real benefit it could offer is available at lower cost through the "alongside" pattern.

## Detailed references

- **`feature-flags-comparison.md`** — full scoring and pros/cons per tool, the performance-tier breakdown, and the final-shortlist reasoning.
- **`feature-flags-use-cases.md`** — three production use cases per option with runtime and sequence diagrams (Mermaid), including the in-house facade pattern.
- **`feature-flags-facade-sketch.md`** — the concrete Java API sketch for the in-house facade and the guardrails that keep it thin.

---

*This is a synthesis for decision-making, not a substitute for the full reasoning in the linked documents.*
