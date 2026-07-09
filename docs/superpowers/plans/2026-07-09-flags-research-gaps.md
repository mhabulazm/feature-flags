# Feature-Flag Facade — Gap & Alternatives Research (v2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce `docs/feature-flags-research-gaps-v2.md` — a six-thread, Consensus-backed research pass identifying gaps, better alternatives, and enhancements for the feature-flag facade project, per the approved spec at `docs/superpowers/specs/2026-07-09-flags-research-gaps-design.md`.

**Architecture:** One Markdown file built incrementally, one thread per task. Each task runs 2-3 Consensus searches, selects the most relevant 2-4 papers, writes a labeled-findings section, and appends to a running References list. Citation numbers continue from 18 (the existing `feature-flags-research-references.md` ends at 17) and increment monotonically task-to-task.

**Tech Stack:** `mcp__claude_ai_Consensus__search` (Consensus MCP), Markdown, git.

## Global Constraints

- Use the Consensus MCP tool for every search; do not substitute web search or memory — the spec requires literature-backed findings.
- Batch at most 3 Consensus search calls at a time per the Consensus MCP server's own instructions (rate-limit avoidance).
- Every finding must be labeled with exactly one of: **Validates**, **Gap**, **Better alternative**, **Enhancement** (per spec's taxonomy).
- Cite papers inline as `[N]` and attribute specific claims to specific papers — never state a finding without a citation.
- Citation format in the References list matches the existing doc exactly: `N. [Title](url) (Authors, Year, Venue, N citations)`.
- Citation numbering starts at 18 and is monotonically increasing across the whole file — each task's first step reserves the next block of numbers based on how many the previous task used.
- Do not re-litigate threads the existing `feature-flags-research-references.md` already settled (vendor lock-in mitigation in general, toggle technical debt in general) — only search where this project's specific questions (engine reconsideration, B1/B2/B3, facade-vs-alternatives, fallback-vs-circuit-breaker) go beyond what's already cited.
- When Consensus tool results are surfaced conversationally (not just written to the file), include the sign-up/usage message from the tool result verbatim, per the Consensus MCP server's own instructions — this applies to chat responses, not to the persisted file content.

---

### Task 1: Scaffold the doc + Thread 1 (engine selection reconsideration)

**Files:**
- Create: `docs/feature-flags-research-gaps-v2.md`

**Interfaces:**
- Consumes: nothing (first task)
- Produces: file exists with intro + Thread 1 section + a References section containing entries `18..K` (K = 17 + count of papers cited in this task); next task consumes `K+1` as its starting citation number.

- [ ] **Step 1: Run Consensus searches**

Call `mcp__claude_ai_Consensus__search` with these two queries (one message, both calls — under the 3-at-a-time limit):
- `"OpenFeature specification vendor-neutral feature flag standard adoption"`
- `"feature flag management platform comparison enterprise 2025"`

- [ ] **Step 2: Select papers**

From the combined results, pick the 2-4 papers most relevant to: (a) whether OpenFeature-standardization is still the strongest argument for GOFF over Unleash, (b) whether any newer/different engine or approach has stronger evidence behind it than either. Skip anything substantially about a different topic than engine standardization/selection.

- [ ] **Step 3: Write the file**

Create `docs/feature-flags-research-gaps-v2.md` with this structure (fill the Thread 1 findings from Step 2's papers, labeling each per the taxonomy):

```markdown
# Feature-Flag Facade — Gap & Alternatives Research (v2)

Second-pass companion to [`feature-flags-research-references.md`](feature-flags-research-references.md) (sources 1-17). This pass targets *gaps*, *better alternatives*, and *enhancements* surfaced by (a) ADR 0002's unratified operating-model amendments (B1/B2/B3) and (b) a code review of the `flags-*` implementation on `main` — not a re-review of what the first pass already validated. Citation numbering continues from 18. Sources found via Consensus on 2026-07-09.

Every finding below is labeled:
- **Validates** — literature supports the current design/code as-is.
- **Gap** — literature identifies a risk or missing piece the project doesn't address.
- **Better alternative** — literature supports a different approach than the one chosen.
- **Enhancement** — literature suggests an addition that improves, but doesn't replace, the current approach.

---

## 1. Engine selection reconsideration

ADR 0002 Part A recommends GO Feature Flag over Unleash primarily because it is OpenFeature-native, citing [8]'s finding that standardized formats/protocols are the strongest vendor lock-in mitigation. This thread checks whether that argument still holds and whether a better-evidenced alternative exists.

[Write 1-3 short paragraphs synthesizing the papers selected in Step 2, each finding labeled and cited, e.g.: "**Validates** — ... [18]." / "**Gap** — ... [19]."]

---

## References

18. [Title](url) (Authors, Year, Venue, N citations)
```

(Replace the bracketed References entries with the real papers from Step 2, continuing the exact citation format used in `feature-flags-research-references.md`.)

- [ ] **Step 4: Verify**

Read the file back and confirm: every `[N]` inline marker has a matching entry in References, numbers are contiguous starting at 18, and every finding has exactly one taxonomy label.

- [ ] **Step 5: Commit**

```bash
git add docs/feature-flags-research-gaps-v2.md
git commit -m "Add v2 research doc: Thread 1 (engine selection reconsideration)"
```

---

### Task 2: Thread 2 — B1 stale-flag governance automation

**Files:**
- Modify: `docs/feature-flags-research-gaps-v2.md`

**Interfaces:**
- Consumes: next citation number `K+1` from Task 1's References list (read the file to find it — don't assume a fixed number).
- Produces: new "## 2. B1 — stale-flag governance automation" section inserted before References; References list extended; next task consumes the new max+1.

- [ ] **Step 1: Run Consensus searches**

- `"automated technical debt detection tooling effectiveness software"`
- `"feature toggle removal automation continuous delivery"`

- [ ] **Step 2: Select papers**

Pick 2-4 papers bearing specifically on whether *automated* (not just notify-only) toggle-lifecycle enforcement is more effective than the "detect and Slack-notify" design in ADR 0002 B1 / `feature-flags-facade-design.md` §6-7.

- [ ] **Step 3: Insert the section**

Using Edit, insert before the `## References` heading:

```markdown
## 2. B1 — stale-flag governance automation

ADR 0002 B1 commits to a stale-flag detection job that notifies the flag's owner; it does not auto-remove anything (kept read-only/advisory per the guardrail). This thread checks whether the literature supports notify-only enforcement or argues for something stronger.

[1-3 paragraphs, labeled findings, citing the papers selected in Step 2 as [K+1], [K+2], ...]
```

Then append the corresponding entries to References, continuing the numbering.

- [ ] **Step 4: Verify**

Same check as Task 1 Step 4, re-run against the whole file (not just the new section) — confirm no duplicate or skipped citation numbers across Threads 1 and 2 combined.

- [ ] **Step 5: Commit**

```bash
git add docs/feature-flags-research-gaps-v2.md
git commit -m "Add v2 research doc: Thread 2 (B1 stale-flag governance)"
```

---

### Task 3: Thread 3 — B2 interaction-scan technique

**Files:**
- Modify: `docs/feature-flags-research-gaps-v2.md`

**Interfaces:**
- Consumes: next citation number from Task 2's References list.
- Produces: new "## 3. B2 — interaction-scan technique" section; extended References; next task consumes the new max+1.

- [ ] **Step 1: Run Consensus searches**

- `"static analysis feature interaction detection software product line"`
- `"combinatorial interaction testing configurable software"`

- [ ] **Step 2: Select papers**

Pick 2-4 papers on static/build-time interaction-detection techniques for configurable/toggle-based software, specifically ones that speak to precision/false-positive tradeoffs versus ADR 0003's co-reference/nesting/cross-service heuristic (Tier 1 scan).

- [ ] **Step 3: Insert the section**

```markdown
## 3. B2 — interaction-scan technique

ADR 0003 adopts a Tier-1 build-time static scan (co-reference, nesting, cross-service categories), explicitly advisory/read-only, with interaction dynamics from [4] as its motivating evidence. This thread checks whether stronger static-analysis techniques exist for the same problem, and at what false-positive cost.

[1-3 paragraphs, labeled findings, cited]
```

Append entries to References.

- [ ] **Step 4: Verify** (same check as prior tasks, whole file)

- [ ] **Step 5: Commit**

```bash
git add docs/feature-flags-research-gaps-v2.md
git commit -m "Add v2 research doc: Thread 3 (B2 interaction-scan technique)"
```

---

### Task 4: Thread 4 — B3 context-resolution resilience (the live code gap)

**Files:**
- Modify: `docs/feature-flags-research-gaps-v2.md`

**Interfaces:**
- Consumes: next citation number from Task 3's References list.
- Produces: new "## 4. B3 — context-resolution resilience" section; extended References; next task consumes the new max+1.

- [ ] **Step 1: Run Consensus searches**

- `"request scoped context propagation distributed systems pattern"`
- `"circuit breaker timeout synchronous service dependency resilience"`

- [ ] **Step 2: Select papers**

Pick 2-4 papers most relevant to: how request-scoped context (tenant/user/traits) should be resolved without adding hot-path I/O, and what a synchronous dependency (e.g. an entitlements-service call) needs (timeout + circuit breaker + declared fallback) per ADR 0002 B3's own requirement.

- [ ] **Step 3: Insert the section**

```markdown
## 4. B3 — context-resolution resilience

This is the one thread with a concrete code artifact to check against: `flags-api`'s `FlagContext.current()` (`flags-api/src/main/java/com/acme/flags/api/FlagContext.java`) currently returns `anonymous()` unconditionally — there is no real context-resolution implementation yet, and ADR 0002 B3 itself notes the docs disagree on whether resolving traits requires I/O (`feature-flags-facade-sketch.md` §3: in-memory bean, no I/O) or not (`feature-flags-use-cases.md` Use Case 1: synchronous Entitlements-service call). This thread checks what the resilience literature says about closing that gap correctly.

[1-3 paragraphs, labeled findings, cited. Explicitly state whether the findings favor the "no I/O" default path or the "live call with circuit breaker" path from B3, and what pattern name applies (e.g. bulkhead, circuit breaker, request-scoped cache).]
```

Append entries to References.

- [ ] **Step 4: Verify** (same check as prior tasks, whole file)

- [ ] **Step 5: Commit**

```bash
git add docs/feature-flags-research-gaps-v2.md
git commit -m "Add v2 research doc: Thread 4 (B3 context-resolution resilience)"
```

---

### Task 5: Thread 5 — facade / anti-corruption-layer pattern validity

**Files:**
- Modify: `docs/feature-flags-research-gaps-v2.md`

**Interfaces:**
- Consumes: next citation number from Task 4's References list.
- Produces: new "## 5. Facade / anti-corruption-layer pattern validity" section; extended References; next task consumes the new max+1.

- [ ] **Step 1: Run Consensus searches**

- `"anti-corruption layer domain driven design integration pattern"`
- `"sidecar pattern versus embedded library microservices tradeoff"`

- [ ] **Step 2: Select papers**

Pick 2-4 papers that bear on whether an in-process facade library (this project's choice, per ADR 0001) is still the best-evidenced shape, versus an out-of-process alternative (sidecar/proxy, similar in spirit to GOFF's own relay-proxy mode).

- [ ] **Step 3: Insert the section**

```markdown
## 5. Facade / anti-corruption-layer pattern validity

ADR 0001 chose an in-process facade library over calling a vendor SDK directly or building a full in-house system. This thread checks whether the literature still supports an in-process facade specifically, versus an out-of-process alternative (e.g. a sidecar/relay-proxy, which GOFF itself supports as a deployment mode per `feature-flags-comparison.md`).

[1-3 paragraphs, labeled findings, cited]
```

Append entries to References.

- [ ] **Step 4: Verify** (same check as prior tasks, whole file)

- [ ] **Step 5: Commit**

```bash
git add docs/feature-flags-research-gaps-v2.md
git commit -m "Add v2 research doc: Thread 5 (facade pattern validity)"
```

---

### Task 6: Thread 6 — DefaultFeatureFlags fallback strategy

**Files:**
- Modify: `docs/feature-flags-research-gaps-v2.md`

**Interfaces:**
- Consumes: next citation number from Task 5's References list.
- Produces: new "## 6. DefaultFeatureFlags fallback strategy" section; extended References; Task 7 consumes the new max+1.

- [ ] **Step 1: Run Consensus searches**

- `"circuit breaker bulkhead pattern fault tolerance comparison microservices"`
- `"broad exception handling anti-pattern fault masking"`

- [ ] **Step 2: Select papers**

Pick 2-4 papers relevant to whether `DefaultFeatureFlags.isEnabled`'s bare `catch (RuntimeException e)` → default-value fallback (see `flags-api/src/main/java/com/acme/flags/api/DefaultFeatureFlags.java`) is adequate, or whether the literature argues for a more structured pattern (circuit breaker to avoid repeated slow-failure calls, bulkhead to isolate failure, etc.) — directly following up on ADR 0002 B3's own caution that "a bare `try/catch → default`... is insufficient for a synchronous service dependency."

- [ ] **Step 3: Insert the section**

```markdown
## 6. DefaultFeatureFlags fallback strategy

`DefaultFeatureFlags` (`flags-api/src/main/java/com/acme/flags/api/DefaultFeatureFlags.java`) catches `RuntimeException` around the engine call and falls back to `FlagMetadata.defaultValue()`, emitting a `feature_flag.evaluated` counter tagged `outcome=fallback`. This is a plain try/catch, not a circuit breaker — no state is kept about repeated failures. ADR 0002 B3 already flags this pattern as insufficient once a synchronous dependency (e.g. a live entitlements call) is in the picture. This thread checks what the fault-tolerance literature recommends.

[1-3 paragraphs, labeled findings, cited]
```

Append entries to References.

- [ ] **Step 4: Verify** (same check as prior tasks, whole file)

- [ ] **Step 5: Commit**

```bash
git add docs/feature-flags-research-gaps-v2.md
git commit -m "Add v2 research doc: Thread 6 (DefaultFeatureFlags fallback strategy)"
```

---

### Task 7: Recommended-actions table, cross-references, final polish

**Files:**
- Modify: `docs/feature-flags-research-gaps-v2.md`
- Modify: `docs/feature-flags-research-references.md`
- Modify: `adr/0002-select-flag-engine-and-evolve-facade-operating-model.md`

**Interfaces:**
- Consumes: the completed 6-section file with References ending at whatever number Task 6 reached.
- Produces: finished, cross-referenced doc; no further task depends on this one.

- [ ] **Step 1: Write the recommended-actions table**

Using Edit, insert a new section directly before `## References` in `docs/feature-flags-research-gaps-v2.md`:

```markdown
## Recommended actions

| Thread | Finding type(s) | Recommended next step |
|---|---|---|
| 1. Engine selection | [fill from Thread 1's labels] | [e.g. "No action — validates ADR 0002 Part A" or "Revisit Gate 1/2 wording in ratification checklist"] |
| 2. B1 governance | [fill] | [fill] |
| 3. B2 interaction scan | [fill] | [fill] |
| 4. B3 context resolution | [fill] | [e.g. "Implement FlagContext.current() per finding — file as a follow-up task, not this research pass"] |
| 5. Facade pattern validity | [fill] | [fill] |
| 6. Fallback strategy | [fill] | [fill] |
```

Fill each row from the actual labeled findings written in Tasks 1-6 — every row with a **Gap** or **Better alternative** finding must have a concrete next step (e.g. "amend ADR 0002 §B3", "file follow-up ADR", "open an implementation task against `FlagContext.java`"); rows that were only **Validates** get "No action — validated."

- [ ] **Step 2: Cross-reference from the original research doc**

Read `docs/feature-flags-research-references.md`, then use Edit to add one line directly under the title (line 1-2) pointing to the new doc:

```markdown
> **Second pass:** see [`feature-flags-research-gaps-v2.md`](feature-flags-research-gaps-v2.md) for a follow-up thread targeting gaps, alternatives, and enhancements found during code review (engine reconsideration, B1/B2/B3, facade pattern validity, fallback strategy).
```

- [ ] **Step 3: Cross-reference from ADR 0002**

Read `adr/0002-select-flag-engine-and-evolve-facade-operating-model.md`, then use Edit to add a line to its "References" section:

```markdown
- `../docs/feature-flags-research-gaps-v2.md` — follow-up research pass on gaps/alternatives/enhancements, including a live code-review finding on `FlagContext.current()` (Thread 4)
```

- [ ] **Step 4: Final verification**

Read the complete `docs/feature-flags-research-gaps-v2.md` and confirm:
- Every `[N]` inline citation has exactly one matching References entry, no gaps or duplicates, contiguous from 18.
- Every finding across all 6 threads has exactly one taxonomy label.
- The recommended-actions table has one row per thread with no placeholder text remaining.
- Both cross-reference edits (Steps 2-3) landed correctly (`git diff` those two files to confirm).

- [ ] **Step 5: Commit**

```bash
git add docs/feature-flags-research-gaps-v2.md docs/feature-flags-research-references.md adr/0002-select-flag-engine-and-evolve-facade-operating-model.md
git commit -m "Finish v2 research doc: recommended actions + cross-references"
```

---

## Self-Review Notes

- **Spec coverage:** all 6 threads from the spec map 1:1 to Tasks 1-6; the spec's "closing recommended actions table" and cross-reference requirements map to Task 7. Citation continuation from 18 is enforced via each task's Interfaces block.
- **No fabricated citations:** every task's References entries are produced from live Consensus results at execution time, not pre-written — this is the one place this plan departs from "no placeholders," and it's inherent to a literature-search deliverable (same as a code plan can't pre-state an API response it hasn't fetched yet).
- **Type/number consistency:** citation numbering is threaded task-to-task via each task's Interfaces block (`Consumes: next number from previous task`) rather than hardcoded, so a task executed out of order or after a differently-sized prior task still gets the right starting number by reading the file.
