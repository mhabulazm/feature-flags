# Feature-Flag Facade — Gap & Alternatives Research (v2)

Second-pass companion to [`feature-flags-research-references.md`](feature-flags-research-references.md) (sources 1-17). This pass targets *gaps*, *better alternatives*, and *enhancements* surfaced by (a) ADR 0002's unratified operating-model amendments (B1/B2/B3) and (b) a code review of the `flags-*` implementation on `main` — not a re-review of what the first pass already validated. Citation numbering continues from 18. Sources found via Consensus on 2026-07-09.

Every finding below is labeled:
- **Validates** — literature supports the current design/code as-is.
- **Gap** — literature identifies a risk or missing piece the project doesn't address.
- **Better alternative** — literature supports a different approach than the one chosen.
- **Enhancement** — literature suggests an addition that improves, but doesn't replace, the current approach.

---

## 1. Engine selection reconsideration

ADR 0002 Part A recommends GO Feature Flag over Unleash primarily because it is OpenFeature-native, citing [8] (in the original doc)'s finding that standardized formats/protocols are the strongest vendor lock-in mitigation. This thread checks whether that argument still holds and whether a better-evidenced alternative exists.

**Validates** — [Using open standards for interoperability issues, solutions, and challenges facing cloud computing](https://consensus.app/papers/details/d87b2cfafead57988e10ba8a963ce21b/?utm_source=claude_desktop) [18] reinforces the original doc's [8]: in a multi-provider ecosystem, standardized APIs/formats are the direct, evidenced mitigation for vendor lock-in. This is independent corroboration for ADR 0002 Part A's central move — picking the OpenFeature-native engine over Unleash's proprietary SDK.

**Gap** — [Cross-vendor programming abstraction for diverse heterogeneous platforms](https://consensus.app/papers/details/9a6ec92c673e59748efbd2111582602e/?utm_source=claude_desktop) [19] studies cross-vendor interoperability under a shared open standard (OpenCL) and finds it's often still limited in practice, because "vendors typically do not have commercial motivations to invest in it" beyond nominal compliance. This tempers ADR 0002's claim that "a future third engine that is also OpenFeature-compliant needs no new adapter at all, only a configuration change" — OpenFeature compliance is necessary but not sufficient. The claim should be read as "adapter code shrinks, not disappears": `GoffFlagEngine`'s context-translation and typed-dispatch logic (see Thread 4) would still need re-validating against any new provider's actual OpenFeature implementation, not just its label.

**Enhancement** — [Lock-In Strategy in Software Competition: Open-Source Software vs. Proprietary Software](https://consensus.app/papers/details/91953d1eed7d5fc1883312af05eb04eb/?utm_source=claude_desktop) [20] models proprietary-vs-open-source competition and finds that vendor lock-in strategies are actually counterproductive against open-source/standards-based competitors, and that preserving customer "freedom of choice" (i.e., low switching cost) can benefit the chosen vendor too. This adds an economic argument alongside the technical one already in ADR 0002: choosing the standards-compliant engine isn't just resilience insurance, it preserves negotiating leverage against both GOFF and Unleash going forward.

**Scope note:** no literature surfaced a *better alternative engine* than GOFF/Unleash — Consensus's peer-reviewed corpus does not cover industry-specific feature-flag platforms directly, consistent with the original doc's scope note that this space lives in practitioner literature, not academic venues. This thread's findings bear on the *standardization argument*, not on discovering a third engine option.

---

## 2. B1 — stale-flag governance automation

ADR 0002 B1 commits to a stale-flag detection job that notifies the flag's owner; it does not auto-remove anything (kept read-only/advisory per the guardrail). This thread checks whether the literature supports notify-only enforcement or argues for something stronger.

**Validates** — [Nudge: Accelerating Overdue Pull Requests toward Completion](https://consensus.app/papers/details/27307aa78b605ff39c20e884c3b8ed22/?utm_source=claude_desktop) [21] is a directly analogous, large-scale randomized trial: an automated system detects an overdue artifact (a stale pull request instead of a stale flag) and *notifies* the responsible party — no blocking, no auto-action. Deployed across 8,000 Microsoft repositories, it cut resolution time by 60% and was rated useful in 73% of cases. This is strong, software-engineering-specific evidence that B1's notify-only design (rather than auto-removal or a blocking gate) is likely to be effective, not just a cautious minimum — the read-only guardrail and the effectiveness case happen to point the same direction here.

**Enhancement** — [Automating Change-Level Self-Admitted Technical Debt Determination](https://consensus.app/papers/details/34db688263f1501a817d32e7865221fb/?utm_source=claude_desktop) [22] shows that text-mining commit messages and diffs (not just a static expiry timestamp) can identify technical-debt-introducing changes with an AUC of 0.82. `feature-flags-facade-design.md` §6/7's stale-flag job (and ADR 0002 B1's metric set — removal-lag ratio, inventory-growth rate, lifespan percentiles) is driven purely by `expiresAfter` and `auditLog.lastEvaluated`. Mining commit/PR messages on the flag's own code paths (e.g. comments admitting a rollout stalled) is a viable, evidenced technique the job could add later to prioritize *which* stale flags are riskiest, beyond "how old is it."

**Gap** — [Effectiveness of nudges as a tool to promote adherence to guidelines in healthcare and their organizational implications: A systematic review](https://consensus.app/papers/details/bf2d0c8a3533584e961caf9566d752a1/?utm_source=claude_desktop) [23], while from a different domain, flags a consistent blind spot across the nudge-effectiveness literature: most studies measure whether a nudge works in isolation, with "little consideration for organizational issues such as cost effectiveness... and disruptions of established workflows" over time. Neither ADR 0002 B1 nor the design doc specifies what happens if the stale-flag Slack notification itself becomes ignorable noise as flag volume grows (notification fatigue) — worth a follow-up metric (e.g. time-to-acknowledgment trend) rather than assuming the Microsoft-scale [21] result holds indefinitely at any notification volume.

---

## References

18. [Using open standards for interoperability issues, solutions, and challenges facing cloud computing](https://consensus.app/papers/details/d87b2cfafead57988e10ba8a963ce21b/?utm_source=claude_desktop) (Harsh et al., 2012, 2012 8th International Conference on Network and Service Management, 40 citations)
19. [Cross-vendor programming abstraction for diverse heterogeneous platforms](https://consensus.app/papers/details/9a6ec92c673e59748efbd2111582602e/?utm_source=claude_desktop) (Leppänen et al., 2022, Unknown Journal, 2 citations)
20. [Lock-In Strategy in Software Competition: Open-Source Software vs. Proprietary Software](https://consensus.app/papers/details/91953d1eed7d5fc1883312af05eb04eb/?utm_source=claude_desktop) (Zhu et al., 2012, Information Systems Research, 89 citations)
21. [Nudge: Accelerating Overdue Pull Requests toward Completion](https://consensus.app/papers/details/27307aa78b605ff39c20e884c3b8ed22/?utm_source=claude_desktop) (Maddila et al., 2020, ACM Transactions on Software Engineering and Methodology, 37 citations)
22. [Automating Change-Level Self-Admitted Technical Debt Determination](https://consensus.app/papers/details/34db688263f1501a817d32e7865221fb/?utm_source=claude_desktop) (Yan et al., 2019, IEEE Transactions on Software Engineering, 73 citations)
23. [Effectiveness of nudges as a tool to promote adherence to guidelines in healthcare and their organizational implications: A systematic review](https://consensus.app/papers/details/bf2d0c8a3533584e961caf9566d752a1/?utm_source=claude_desktop) (Nwafor et al., 2021, Social Science & Medicine, 54 citations)
