# v1 Slice B -- Gate 2 Self-Serve Runbook

Operational runbook for gathering [ADR 0002 ratification checklist](../adr/0002-ratification-checklist.md) Gate 2 evidence. This is **not code** -- it's a script for a human to execute and a template for recording what happens. See the [Slice B spec](superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md) for why.

## Before running this

Gather this from stakeholders first -- it's the actual requirement Gate 2 is testing, and nothing in this repo can supply it:

- **Who** actually needs to change flags without engineering involvement?
- **How often** would they need to?

Record the answer here before proceeding:

> **Recorded 2026-07-10 (current sandbox reality, not a forecast):** No non-engineer stakeholder exists for this facade yet. It is a single-committer sandbox library with no consuming product team, no PM, and no ops flag-flippers. All flag changes today are made by the one engineer via GitOps (PR to `flags.yaml` / the service `FlagKey` registry). The real demand for non-engineer self-serve -- who needs it and how often -- is genuinely unknown until a consuming product team adopts the facade, and is deliberately **not** invented here.

## Exercise 1 -- GOFF admin UI

Recruit one person who is **not an engineer** (a PM, support lead, ops person -- whoever the answer above names). Give them this task, unaided beyond what the UI itself offers:

> "Using the GOFF admin UI at `<url>`, turn on the flag `benchmark-flag` for 100% of traffic. Let me know when you're done."

Record:

| Metric | Value |
|---|---|
| Time to complete | |
| Number of times they asked for help | |
| Friction points observed | |
| Would they describe this as something they could do again unaided? | |

## Exercise 2 -- flags-as-config / GitOps

Same person (or a comparable one), same underlying change, via the GitOps path instead:

> "Open a pull request changing `flags.yaml` to turn on `benchmark-flag` for 100% of traffic. Let me know when it's merged and deployed."

Record:

| Metric | Value |
|---|---|
| Time to complete (incl. PR review wait) | |
| Number of times they asked for help | |
| Friction points observed | |
| Would they describe this as something they could do again unaided? | |

## Verdict

Per the checklist's Gate 2 pass criterion -- either:

- [ ] A non-engineer completed Exercise 1 without a code deploy, through a workflow the team accepts as adequate self-serve, **or**
- [x] The team explicitly accepts flags-as-config/GitOps (Exercise 2) as sufficient self-serve for the foreseeable roadmap, given the "who/how often" answer recorded above.

**Verdict: PROVISIONAL PASS via path (b) -- recorded 2026-07-10.** Given the who/how-often reality above (no non-engineer consumer exists yet), flags-as-config / GitOps (PR-per-change) is accepted as sufficient self-serve for the current roadmap -- coherent with the facade's GitOps-native design (per-service `FlagKey` registries, `flags.yaml`, PR review = flag review).

**Exercises 1 and 2 were NOT run** -- the sandbox has no live GOFF admin UI and no non-engineer to recruit, and this runbook forbids fabricating results. This verdict rests on path (b)'s *decision*, not on gathered usability evidence.

**Mandatory re-run trigger:** when a real consuming team onboards with non-engineer flag-flippers (PM/ops), run Exercise 1 (GOFF admin UI) vs Exercise 2 (GitOps) against a real "PM toggles a flag" task and record a real verdict. If GitOps proves inadequate for those users, Gate 2 flips to FAIL and Unleash's first-class self-serve becomes the fallback per the checklist's decision rule.

Feed this verdict into `adr/0002-ratification-checklist.md`'s Gate 2 sign-off row.
