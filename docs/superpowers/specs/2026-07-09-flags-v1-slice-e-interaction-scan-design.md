# v1 Slice E — ADR 0003 Tier 1 Interaction Scan

## Status

Tier 1a implementation design **approved 2026-07-10** (see the "Tier 1a implementation design" section below). ADR 0003 is now *Accepted*, so **Tier 1a is unblocked and ready to implement**; Tier 1b (cross-repo aggregation) remains blocked on the absence of any consuming service to aggregate from. Part of the [v1 roadmap](../../feature-flags-v1-roadmap.md).

## Purpose

ADR 0003 commits to a build-time static scan across every service's `FlagKey` registry, detecting flag interactions (co-reference, nesting, cross-service reference) and emitting a read-only, advisory report — never blocking merges, never mutating anything. This slice builds that.

---

## Requirements (per ADR 0003's Decision section)

- **Aggregate** every `FlagKey` registry (all enums implementing `flags-api`'s `FlagKey` interface) into one inventory of known keys and their owning service.
- **Scan source** for flag-key references, surfacing keys referenced in combination, in three categories:
  - **Co-reference** — two or more flag keys evaluated in the same method or conditional expression.
  - **Nesting** — a flag's enabled/disabled branch that itself contains another flag's evaluation.
  - **Cross-service** — a service referencing a flag key from another service's namespace (detectable via the `service.flag-name` key-string convention `feature-flags-facade-design.md` §5 already establishes).
- **Emit an interaction report** — read-only, advisory. Warns and notifies; does not block merges, does not mutate, toggle, or persist an authoritative copy of anything. Runs alongside the B1 job ([Slice D](2026-07-09-flags-v1-slice-d-stale-flag-governance-design.md)) under the same owner.

---

## The biggest unresolved gap: cross-repo aggregation

ADR 0003 itself lists this as an explicitly open follow-up ("how a fleet-level scan discovers and reads every service's `FlagKey` registry — monorepo view, a published registry artifact per service, or a CI aggregation step. Decide before implementation."). Nothing has decided it since. This repo makes the gap worse, not better: **this repo is the facade library itself, not a monorepo of consuming services** — there is no `checkout-service`, no `billing-service`, nothing to aggregate. Even the "monorepo view" option has nothing to point at today.

**Recommended scope split for this slice**, to avoid blocking all of Slice E on a cross-repo mechanism that has no real target yet:

- **Tier 1a — single-service static scan (buildable now).** The actual co-reference/nesting/cross-service-reference detection logic, implemented as a standalone, reusable scanner (likely via a Java source-analysis library — JavaParser or similar — parsing `.java` files for references to `FlagKey`-implementing enum constants). Testable today against synthetic fixture registries and synthetic call sites this slice builds itself, exactly like Slice D's fixture problem. This is genuinely completable without waiting on any other service to exist.
- **Tier 1b — cross-repo aggregation wiring (blocked).** Whichever mechanism gets chosen (published-artifact-per-service is the most plausible candidate given no monorepo exists and a CI-aggregation step needs somewhere real to check out from) has no consuming service to test against yet. This half of "Tier 1" cannot be meaningfully built or validated until at least one real service with its own `FlagKey` registry exists.

This split is a recommendation this spec is making, not a decision ADR 0003 has ratified — flag it for review when this slice is picked up. **(Reviewed and confirmed 2026-07-10; the "Tier 1a implementation design" section below builds on it.)**

## Other gaps (ADR 0003's own "Follow-ups / open questions," still open)

- **Expected false-positive rate isn't set anywhere, and should be before this ships.** `feature-flags-research-gaps-v2.md` Thread 3 found a structurally similar lightweight static scan (co-reference-style pattern matching, same advisory posture) ran at roughly **90% false-positive rate** on raw findings in a real codebase [24]. ADR 0003 doesn't cite this number anywhere. This slice's spec sets the expectation explicitly so the team isn't surprised post-ship: expect the bulk of raw findings to be reviewed and dismissed, not confirmed — the advisory-only contract (a review note, not a blocked merge) is the correct mitigation for exactly this, but only if people know to expect it going in.
- **A "Tier 1.5" improvement exists in the literature and isn't recorded in ADR 0003.** Thread 3 [25] also found that triaging new findings by similarity to *previously confirmed* interactions meaningfully improves precision (83% accuracy, 60–100% coverage in evaluation) — but it needs a corpus of confirmed interactions to compare against, which won't exist on day one. Worth noting as a natural v2 for this scan once Tier 1a has accumulated reviewed findings, not something to build in this slice.
- **Interaction heuristic precision** — the exact rule for "referenced in combination" (AST co-reference scope, how deep nesting is followed) is unspecified beyond the three named categories above. This slice has to make that call; ADR 0003 doesn't.
- **Graduation criteria and the Tier 2 escalation threshold are both still "set at ratification"** per ADR 0003 — not real numbers yet. This slice should not invent them; it should build the scan to be advisory-only as specified and leave the threshold-setting to whoever eventually reviews accumulated findings against ADR 0003's own escalation triggers (an incident traced to something the scan structurally couldn't see; interactions becoming materially dynamic/config-driven; or a team-set count threshold).

## Tier 1a implementation design (approved 2026-07-10)

### Module & packaging

A new module **`flags-interaction-scan`** — a standalone build tool, like `flags-benchmark-goff`, **deliberately excluded from `flags-bom`**. Nothing in the facade runtime depends on it; ADR 0003 scopes it as CI tooling that "runs alongside the B1 job under the same owner," not shipped API. Dependencies: `com.github.javaparser:javaparser-core:3.28.2`, plus JUnit 5 + AssertJ (test). Java 21, same parent POM. It does **not** depend on `flags-api` — the scanner reads source text, it does not link against the facade types.

### Architecture — four small units, source-in → advisory-report-out

1. **`FlagRegistryIndex`** — parses all `.java` under the given roots, finds enums whose `implements` clause names `FlagKey`, and collects each enum constant together with its **namespace** (the prefix of its `key()` string literal before the first `.`, e.g. `billing` from `billing.rate-limit-override`). This is ADR 0003's required "inventory of known keys and their owning service."
2. **`FlagReferenceScanner`** — walks method bodies and records a `FlagReference` for each indexed constant used as an argument to a `FeatureFlags` evaluation call (`isEnabled` / `getVariant` / `getConfigValue`), capturing file:line, the enclosing method, and the enclosing `if`-branch chain.
3. **`InteractionDetector`** — applies the three category rules (below) over the collected references and emits `Interaction` findings.
4. **`InteractionReport`** + a CLI entrypoint **`InteractionScanMain`** — render the findings read-only; runnable via the `exec-maven-plugin`, the same way the benchmark module runs.

**Detection is purely *syntactic* in Tier 1a — no symbol-solver, no compilation.** JavaParser matches the `implements FlagKey` clause and argument positions by name and structure alone, without resolving types against a classpath. This is a deliberate precision trade: a name collision, or a non-facade method that happens to be called `isEnabled`, will match and produce a false positive. That is exactly the ~90%-false-positive reality [24] the advisory-only contract exists to absorb. Full symbol resolution (via `javaparser-symbol-solver-core`) is noted as a Tier-1.5 precision enhancement, not built in this slice.

### Detection rules

ADR 0003 names the three categories but leaves their exact rules to implementation. Tier 1a pins them as:

- **Co-reference** — two or more *distinct* `FlagKey` constants referenced within the same method body. Two keys in the same conditional expression are reported as a stronger sub-signal of the same category.
- **Nesting** — a `FlagKey` reference located inside the then/else block of an `if` whose condition references a *different* `FlagKey`. Followed transitively through nested `if`s, recording nesting depth.
- **Cross-service** — a reference whose `FlagKey` namespace differs from the enclosing file's owning namespace (the namespace of the registry the file declares, else its package). Since no real services exist, fixtures model a service as a package.

Every finding carries its category, the key(s) involved, and the referencing site (file:line) — exactly ADR 0003's "interacting key pairs/groups, with the referencing site."

### Output — read-only, advisory (a structural contract)

The scan emits an interaction report: findings grouped by category, each with its keys and site. Plain text by default, `--json` for a machine-readable form. It **exits 0 regardless of findings, and contains no code path that mutates, toggles, or persists anything.** ADR 0003's and ADR 0002's read-only/advisory guardrail is therefore enforced by construction, not by convention — distinct from Slice D's metadata CI gate, which *is* allowed to block. The report header states the ~90%-false-positive expectation so output is triaged, not trusted.

### Testing — synthetic fixtures

Because no real services or registries exist, the scanner is proven against synthetic fixture `.java` files under `src/test/resources/fixtures/` (parsed as source text; they need not compile). One fixture tree per category — co-reference, nesting, cross-service (two packages standing in for two services) — plus a **negative** fixture of isolated single-flag uses that must produce zero findings. Unit tests assert each detector finds exactly the planted interactions and nothing in the negative case.

## Out of scope

- Tier 2 (the deferred runtime interaction subsystem) — stays deferred behind ADR 0003's own escalation trigger; gets its own future ADR 0004 if triggered. Not this slice's concern at all.
- Any blocking behavior — this scan is advisory only, permanently, per ADR 0003's Guardrails section (distinct from Slice D's CI metadata gate, which *is* allowed to block).
- Tier 1.5 similarity-based triage — noted above as a future enhancement once a confirmed-interaction corpus exists, not built here.

## References

- [v1 roadmap](../../feature-flags-v1-roadmap.md)
- [Slice D — B1 stale-flag governance](2026-07-09-flags-v1-slice-d-stale-flag-governance-design.md) — shares an owner and the same registry-discovery problem
- `../../../adr/0003-cross-service-flag-interaction-scan.md` — Decision, Guardrails, Follow-ups sections
- `../../../adr/0002-ratification-checklist.md` — the B2/ADR-0003-scope blocker this slice's gate ties to
- `../../feature-flags-research-gaps-v2.md` Thread 3, sources [24] and [25]
- `../../feature-flags-facade-design.md` §5 — the per-service `FlagKey` registry / namespacing convention this scan aggregates over
