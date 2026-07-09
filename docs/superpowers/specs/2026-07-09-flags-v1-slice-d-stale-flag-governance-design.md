# v1 Slice D — B1 Stale-Flag Governance + CI Metadata Gate

## Status

Draft — **blocked** on ADR 0002 *Accepted*. Content is engine-agnostic (see open question below on whether that's a hard gate or just a procedural one). Part of the [v1 roadmap](../../feature-flags-v1-roadmap.md).

## Purpose

ADR 0002 Part B, B1 commits — as a shipped deliverable, not an example — to two things: a stale-flag detection job, and a CI gate on registry-entry metadata. Both were sketched illustratively in `feature-flags-facade-sketch.md` §7 but never built. This slice builds them for real.

---

## Requirement 1 — Stale-flag detection job

Per ADR 0002 B1: emit the metric set from source [6] in `feature-flags-research-references.md` (Tërnava, *Feature Toggle Dynamics in Large-Scale Systems*) — **removal-lag ratio, inventory-growth rate, and lifespan percentiles** — not just a binary "past `expiresAfter`" check. **Gap:** this spec does not have the paper's exact metric definitions in hand; whoever implements this should read source [6] directly rather than guessing a formula. As a starting shape: removal-lag ratio (removals ÷ additions over a rolling window), inventory-growth rate (net registry size change over time), and lifespan percentiles (p50/p90/p99 age at removal, or current age for still-live flags) are the right *kind* of metrics, but the precise computation needs the source, not this spec.

**Guardrail (binding, from ADR 0002 and restated in ADR 0003):** this job is **read-only and advisory**. It files a notification; it never auto-removes, disables, or mutates a flag. ADR 0003's own Guardrails section explicitly contrasts this with itself being advisory-only, "distinct from B1's metadata CI gate... which does block" — i.e. the *detection job* is advisory, the *CI gate* (Requirement 2 below) is allowed to block. Don't conflate the two.

### Gaps

- **No real `FlagKey` registry exists anywhere in this repo to run against.** Grepped: the only `implements FlagKey` in the whole codebase are two test-only records (`flags-test-fixtures`, `flags-api` tests). This job needs synthetic fixture registries built as part of this slice to develop and test against — there is no `CheckoutFlags`-style example anywhere, only the illustrative one in `feature-flags-facade-sketch.md`'s markdown.
- **Registry discovery mechanism is undecided.** Each service owns its own `FlagKey` enum(s) by design (`feature-flags-facade-design.md` §5 — no global enum). For a job running *inside* one service, how does it find all of that service's own `FlagKey` implementations? Two options, neither decided: (a) classpath scanning bounded to the consuming service's own base package (doesn't violate registry decentralization — it's scanning one service's own code, not aggregating across services, which is Slice E's separate concern), or (b) explicit registration, where each service lists its registry classes in a Spring property or `@Bean`. This spec doesn't pick one; whoever implements this should.
- **Notification sink is undecided.** The original sketch (`feature-flags-facade-sketch.md` §7) hardcodes `slackNotifier.notify(...)`; nothing Slack-specific exists in this repo, and there's no notification abstraction anywhere. Recommend a small SPI (`StaleFlagNotifier` or similar, mirroring the `FlagEngine`/`FlagContextResolver` SPI pattern already established) with a logging-only default implementation for v1, leaving real Slack/email/etc. wiring to each consuming service — but this is a recommendation, not a decision already made.
- **Non-blocking backlog item:** `feature-flags-research-gaps-v2.md` Thread 2's Gap [23] — notification fatigue as flag volume grows, unaddressed by any doc — should get a time-to-acknowledgment trend metric eventually. Not required for this slice to ship; tracked here so it isn't lost.

---

## Requirement 2 — CI metadata gate

Per ADR 0002 B1: block on registry entries missing `owner`, `ticket`, or `expiresAfter` (or an explicit "permanent" marker), enforced mechanically rather than by reviewer memory.

### A significant chunk of this already exists

`FlagMetadata`'s compact constructor (`flags-api/src/main/java/com/acme/flags/api/FlagMetadata.java`) already does `Objects.requireNonNull` on `owner`, `ticket`, and `failureSemantics` — a missing `owner` or `ticket` already throws at construction time (which, for an enum constant, means at class-initialization time — effectively caught by any test or code path that touches the enum, i.e. already CI-enforced today, just via a raw `NullPointerException` rather than a purpose-built lint message). This slice's real remaining work is narrower than the ADR text implies:

- **A nicer failure than a raw NPE** (nice-to-have — a nicer error message naming the offending constant, not a functional gap).
- **The `expiresAfter`/"permanent" distinction is genuinely unenforceable today, and that's a real gap, not just missing tooling.** `FlagMetadata`'s own doc comment already treats `expiresAfter == null` as meaning "permanent" ("`Duration expiresAfter // null = permanent`"). But that means a flag author who simply *forgot* to set `expiresAfter` is indistinguishable, by the type alone, from one who *deliberately* declared a permanent config flag — there is no way to mechanically enforce "must be set unless genuinely permanent" when "unset" and "intentionally permanent" are the same value. Closing this for real needs a small `FlagMetadata` schema addition (e.g. an explicit `boolean permanent` field separate from `expiresAfter`, so the two states are distinguishable) — which is itself a versioning decision (a new field on an existing record is source-compatible via the builder, consistent with `facade-design.md` §6's MINOR-bump rule for additive changes, but worth flagging explicitly since it changes what every future `FlagKey` registry entry is expected to declare).

### Gaps

- Same "nothing to enforce against" problem as Requirement 1 — needs synthetic fixture registries.
- **Open question, not resolved here:** does this slice's *content* need to wait for ADR 0002's full ratification (engine pick + both gates + all non-engine blockers), or could it start today since it has zero engine dependency? ADR 0002's Status line is a blanket gate over the whole document (Part A and B together), but the content itself doesn't touch GOFF, Unleash, or any engine choice. Surfaced here for whoever schedules this slice's start date, not decided by this spec.

---

## Out of scope

- Any real integration with an actual notification service (Slack, PagerDuty, etc.) — the SPI/interface, if built, ships with a logging-only default only.
- Deciding whether D ships as an early `0.x` pre-release or waits for the bundled `1.0.0` cut alongside Slices C and E — see the [v1 roadmap](../../feature-flags-v1-roadmap.md) §3.

## References

- [v1 roadmap](../../feature-flags-v1-roadmap.md)
- `../../../adr/0002-select-flag-engine-and-evolve-facade-operating-model.md` — Part B, B1; Guardrails section
- `../../../adr/0003-cross-service-flag-interaction-scan.md` — Guardrails section (the B1-blocks-vs-B2-advisory distinction)
- `../../feature-flags-facade-sketch.md` §7 — the illustrative `reportStaleFlags` sketch this formalizes
- `../../feature-flags-research-references.md` source [6] — the metric set (removal-lag ratio, inventory-growth rate, lifespan percentiles)
- `../../feature-flags-research-gaps-v2.md` Thread 2 — notification-fatigue backlog item
- `../../../flags-api/src/main/java/com/acme/flags/api/FlagMetadata.java`
