# v1 Slice B -- Gate 2 Self-Serve Runbook

Operational runbook for gathering [ADR 0002 ratification checklist](../adr/0002-ratification-checklist.md) Gate 2 evidence. This is **not code** -- it's a script for a human to execute and a template for recording what happens. See the [Slice B spec](superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md) for why.

## Before running this

Gather this from stakeholders first -- it's the actual requirement Gate 2 is testing, and nothing in this repo can supply it:

- **Who** actually needs to change flags without engineering involvement?
- **How often** would they need to?

Record the answer here before proceeding:

> _(fill in before the exercise -- do not fabricate a plausible-sounding answer)_

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
- [ ] The team explicitly accepts flags-as-config/GitOps (Exercise 2) as sufficient self-serve for the foreseeable roadmap, given the "who/how often" answer recorded above.

Feed this verdict into `adr/0002-ratification-checklist.md`'s Gate 2 sign-off row.
