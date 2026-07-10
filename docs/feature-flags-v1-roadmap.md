# Feature-Flag Facade — v1 Roadmap

The single place that lays out the full v1 picture as it's described across ADR 0001 (Accepted), ADR 0002 (Proposed/draft), ADR 0003 (Proposed/draft), and the facade design docs — what's shipped, what's ready to build now, what's blocked and on what, and what's still an open question nobody has answered yet. It supersedes/extends `feature-flags-facade-design.md` §8's "Rollout plan," written before ADR 0002 Part B added B1/B2/B3 to the scope of "v1."

Five deliverable slices remain (A–E below); each has its own detailed spec under `docs/superpowers/specs/2026-07-09-flags-v1-slice-*-design.md`. This document sequences them and flags what they individually can't resolve.

---

## 1. v1 scope statement

**In scope for v1 (cuts as `1.0.0` per `feature-flags-facade-design.md` §6):**
- The core facade (`flags-api`, `flags-noop`, `flags-test-fixtures`, `flags-contract-test`, `flags-spring-boot-starter`, `flags-bom`) — **done**, shipped on `main`.
- A real request-scoped `FlagContext.current()` and a narrowed exception-handling contract in `DefaultFeatureFlags` — Slice A.
- One functioning engine adapter (`flags-engine-goff` if GOFF is ratified; an unwritten `flags-engine-unleash` if Unleash is ratified instead) — Slice C.
- B1, automated stale-flag governance — Slice D.
- B2 / ADR 0003 Tier 1, the build-time cross-service interaction scan — Slice E.

**Explicitly out of scope for v1:**
- ADR 0003 Tier 2 (the deferred runtime interaction subsystem) — stays deferred behind its own escalation trigger; gets ADR 0004 if it ever fires.
- Full remote-config semantics beyond the typed-variant wrapper (`feature-flags-facade-design.md` §3's v1 scope note already defers this).
- `flags-engine-unleash`, unless ratification (Gate 1 or Gate 2 failure) flips the engine choice to Unleash.
- The `japicmp`/`revapi` binary-compatibility gate and a real `mvn deploy` to Nexus. **Open question, not a decision:** this is a sandbox project with no real Nexus target today — it's unclear whether "v1" should include standing up real publishing infrastructure or whether that's permanently out of this project's scope. Nobody has decided this yet.

---

## 2. Slice table

| Slice | What | Status | Blocked by | Spec |
|---|---|---|---|---|
| 0 | Core facade | **Done** | — | (already shipped; no spec needed) |
| A | Facade hardening — `FlagContext.current()`, exception narrowing | Ready now | Nothing | `2026-07-09-flags-v1-slice-a-facade-hardening-design.md` |
| B | Ratification evidence — Gate 1/2 spikes, Parameter Store verification | Ready now | Nothing | `2026-07-09-flags-v1-slice-b-ratification-evidence-design.md` |
| — | **Ratification itself** (human decision) | Not started | Slice B's evidence + the non-code blockers in §4 | — (not a code slice) |
| C | Complete `flags-engine-goff` adapter, cut `1.0.0` | Blocked | Ratification, and specifically GOFF being the chosen engine | `2026-07-09-flags-v1-slice-c-goff-adapter-design.md` |
| D | B1 — stale-flag governance job + CI metadata gate | Blocked | ADR 0002 Accepted | `2026-07-09-flags-v1-slice-d-stale-flag-governance-design.md` |
| E | B2 / ADR 0003 Tier 1 — interaction scan | Ready now (Tier 1a; Tier 1b needs real consumers) | ADR 0003 now **Accepted** (2026-07-10) | `2026-07-09-flags-v1-slice-e-interaction-scan-design.md` |

Slices A and B have no dependency on each other or on ratification — both can start immediately and run in parallel.

---

## 3. The bundling question

ADR 0002's own Consequences section states plainly: **"B1 and B2 are additional build scope in the first release."** Read together with `feature-flags-facade-design.md` §6 ("Cut `1.0.0` once the first real adapter exists and its ADR (0002) is accepted"), this reads as a commitment to ship Slices C, D, and E together as one `1.0.0` cut — not as three independently-releasable tracks.

**This roadmap does not resolve that.** It poses the question explicitly instead of silently deciding it:

- Should D and E be allowed to ship as an earlier `0.x` pre-release ahead of C (nothing in `flags-api`'s own versioning rules forces waiting — it stays `0.x` "until an engine adapter exists and passes `flags-contract-test`," which is a floor, not a ceiling on what else can ship pre-1.0)?
- Or does ADR 0002's "first release" language mean all three genuinely need to land together at `1.0.0`?

Whoever owns Slice C/D/E's actual scheduling needs to answer this before sequencing real work across them — it's not something a per-slice spec can resolve on its own, since it's about the relationship between slices, not any one slice's content.

---

## 4. Non-code blockers

These gate ratification (and therefore Slices C/D/E) but have no home in any of the five code slices — they're organizational decisions, not engineering work:

- **Facade ownership assignment** — which team owns the `flags-*` library, its versioning policy, and the on-call rotation for the evaluation path. **Consciously waived for the sandbox (2026-07-10)** with a recorded intended-owner profile (see `../adr/0002-ratification-checklist.md`); converts to a real assignment at first go-live. B1's governance job (Slice D) and the Slice E scan ride on that eventual owner.
- **ADR 0001's still-open rollout-plan follow-ups** — which service integrates first, and how the per-service `Feature`/`FlagKey` registry and `FailurePolicy` defaults get reviewed before go-live. Named in ADR 0001's own Follow-ups section, separate from the ownership question, and not restated anywhere else.

Neither of these has a spec file. They need a decision-maker, not a design.

---

## 5. Housekeeping backlog

`docs/feature-flags-research-gaps-v2.md`'s own Recommended Actions table names concrete doc edits that were identified but never applied. Tracked here so they aren't lost, not auto-applied as part of this roadmap (they touch ADR text, which deserves its own review rather than a side effect of writing a roadmap):

**Status: all three items below applied 2026-07-10 — this backlog is now cleared.**

- **Thread 1** — ~~reword ADR 0002 Part A's "…needs no new adapter at all, only a configuration change" to "adapter code shrinks, not disappears," per source [19]'s finding that open-standard compliance doesn't guarantee full cross-vendor interoperability without the destination vendor's own investment.~~ **Done (2026-07-10)** — reworded in ADR 0002 Part A (with the [19] caveat), and the same overclaim softened in ADR 0002's Consequences and the ratification-checklist mirror.
- **Thread 3** — ~~add the ~90%-false-positive expectation (source [24]) and the "Tier 1.5" similarity-based triage follow-up (source [25]) to ADR 0003's "Follow-ups / open questions" section.~~ **Done (2026-07-10)** — both folded into ADR 0003's heuristic-precision follow-up at ratification.
- **Thread 5** — ~~add a relay-proxy latency-benchmark action item to ADR 0002/0003's follow-ups, given source [31]'s sidecar-overhead findings (up to 269% higher latency) apply directly to GOFF's relay-proxy deployment mode.~~ **Done (2026-07-10)** — added to ADR 0002's Follow-ups, tied to Gate 1's peak-load evidence (benchmark relay-proxy vs embedded p99).

---

## References

- `feature-flags-facade-design.md` §8 — the rollout plan this roadmap supersedes/extends
- `../adr/0001-adopt-in-house-facade-alongside-flag-engine.md`, `../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md`, `../adr/0002-ratification-checklist.md`, `../adr/0003-cross-service-flag-interaction-scan.md`
- `feature-flags-research-gaps-v2.md` — source of the housekeeping backlog in §5
- `superpowers/specs/2026-07-09-flags-v1-slice-{a,b,c,d,e}-*-design.md` — the five slice specs this roadmap sequences

*This is a living document — update the slice table and the bundling/non-code-blocker sections as gates resolve, not a one-time snapshot.*
