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

## References

18. [Using open standards for interoperability issues, solutions, and challenges facing cloud computing](https://consensus.app/papers/details/d87b2cfafead57988e10ba8a963ce21b/?utm_source=claude_desktop) (Harsh et al., 2012, 2012 8th International Conference on Network and Service Management, 40 citations)
19. [Cross-vendor programming abstraction for diverse heterogeneous platforms](https://consensus.app/papers/details/9a6ec92c673e59748efbd2111582602e/?utm_source=claude_desktop) (Leppänen et al., 2022, Unknown Journal, 2 citations)
20. [Lock-In Strategy in Software Competition: Open-Source Software vs. Proprietary Software](https://consensus.app/papers/details/91953d1eed7d5fc1883312af05eb04eb/?utm_source=claude_desktop) (Zhu et al., 2012, Information Systems Research, 89 citations)
