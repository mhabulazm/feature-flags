# Feature-Flag Facade — Gap & Alternatives Research (v2) — Design

## Status

Approved for research. Scopes a literature-backed audit of the "alongside" facade — its accepted architecture ([ADR 0001](../../../adr/0001-adopt-in-house-facade-alongside-flag-engine.md)), its unratified follow-on ([ADR 0002](../../../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md), [ADR 0003](../../../adr/0003-cross-service-flag-interaction-scan.md)), and the actual `flags-*` Maven implementation on `main`.

## Purpose

Extend the existing literature review ([`feature-flags-research-references.md`](../../feature-flags-research-references.md), 17 sources) with a second pass that specifically hunts for **gaps**, **better alternatives**, and **enhancements** — not a re-litigation of what that document already validated (the alongside pattern itself, the `expiresAfter ≈ 90 days` default). This pass is motivated by two things found during a code review of `main`:

1. Three ADR 0002 operating-model amendments (B1 governance, B2 interaction scan, B3 resilience contract) are specified in prose but not yet built.
2. `FlagContext.current()` in `flags-api` is a stub that unconditionally returns `anonymous()` — the context-resolution design ADR 0002 §B3 describes (and disputes between the sketch doc and the use-cases doc) has no real implementation yet, so this is a live code gap, not just an open design question.

## Explicit scope decisions

1. **Six research threads**, confirmed with the user:
   1. Engine selection reconsideration (GOFF vs. Unleash vs. anything newer)
   2. B1 — stale-flag governance automation, effectiveness beyond notify-only
   3. B2 — interaction-scan technique, alternatives to co-reference heuristics
   4. B3 — context-resolution resilience (maps directly to the `FlagContext.current()` code gap)
   5. Facade / anti-corruption-layer pattern validity vs. alternatives (e.g. sidecar/proxy)
   6. `DefaultFeatureFlags`'s bare `catch (RuntimeException)` fallback vs. structured fault-tolerance (circuit breaker/bulkhead)
2. **Source tool:** Consensus (`mcp__claude_ai_Consensus__search`), same as the original research doc, so citation style and provenance stay consistent.
3. **Findings taxonomy** — every finding is explicitly labeled one of:
   - **Validates** — literature supports the current design/code as-is.
   - **Gap** — literature identifies a risk or missing piece the project doesn't address.
   - **Better alternative** — literature supports a different approach than the one chosen.
   - **Enhancement** — literature suggests an addition that improves, but doesn't replace, the current approach.
4. **Not in scope:** re-researching threads the existing doc already covers well (vendor lock-in mitigation, toggle technical debt in general, the Fallback/Circuit-Breaker patterns at a generic level) — this pass only searches where the existing 17 sources don't already give a clear answer, or where the code review surfaced a *specific* new question the original doc didn't ask (thread 4 and 6 above, primarily).
5. **Citation numbering continues from 18**, so the new doc composes with the existing one rather than duplicating a reference list.

## Output

New file: `docs/feature-flags-research-gaps-v2.md`

Structure:
- Intro / relationship to `feature-flags-research-references.md`
- One section per thread: current state → question → findings (labeled per the taxonomy above) → cited papers
- A closing "recommended actions" table mapping each **Gap**/**Better alternative** finding to a concrete next step (e.g. "amend ADR 0002 B3", "file a follow-up ADR", "no action — validated")
- References section, numbered 18+, appended in the same citation format as the original doc

Cross-references to add in existing files once the new doc lands:
- `feature-flags-research-references.md` — pointer to the v2 doc at the top
- `adr/0002-select-flag-engine-and-evolve-facade-operating-model.md` — reference from Part B where relevant threads land
- `flags-api/src/main/java/com/acme/flags/api/FlagContext.java` — no code change in this pass (research only); the doc's B3 finding is what a future implementation PR would act on

## Out of scope / follow-ups

- Actually implementing any change the research recommends (e.g. wiring `FlagContext.current()`, building the B1/B2 tooling, adding a circuit breaker to `DefaultFeatureFlags`) — this pass produces findings and recommendations only.
- Re-ratifying ADR 0002 — thread 1's findings feed into that decision but don't make it.
