# flags-benchmark-goff (Gate 1 ratification evidence)

Standalone load-test harness producing the peak-load evidence [ADR 0002's ratification
checklist](../adr/0002-ratification-checklist.md) Gate 1 asks for. Not a published
`flags-*` artifact -- deliberately excluded from `flags-bom`; nothing in the facade or
its consumers depends on this module.

## Status

Compiles cleanly against the real `dev.openfeature:sdk:1.15.1` and
`dev.openfeature.contrib.providers:go-feature-flag:0.4.3` dependencies pinned in this
module's `pom.xml` (verified via `mvn -pl flags-benchmark-goff -am clean compile` in the
environment this was built in -- see `GoffBenchmarkHarness`'s class javadoc for exactly
what was checked against the real jars). **Has never actually been run** -- running
`GoffBenchmarkHarness.main()` needs a Docker daemon (for the Testcontainers-managed
`gofeatureflag/go-feature-flag` relay-proxy), which wasn't available in that environment.
Treat the numbers this produces as unverified until someone with Docker access runs it.

## What it does

Runs a rate-limited load test against a real GOFF relay-proxy (started via Testcontainers)
across four throughput tiers (100 / 1,000 / 10,000 / 50,000 req/s, 10 seconds each),
reporting p50/p99/p999 added latency and error rate per tier.

Per the Slice B spec's Gate 1 gaps:
- **No real peak req/s target exists in this repo.** The four tiers above are a sweep, not
  a guess at the real number -- read the real target off the curve at the ratification
  meeting instead of trusting a single pass/fail run.
- **Gate 1 is NOT satisfied by this module as it stands.** Only relay-proxy/`REMOTE`-mode
  evidence is produced -- this is a known gap and a blocker for Slice B / Gate 1 sign-off,
  not a minor caveat and not a design choice. The design spec calls for embedded/library-mode
  (`IN_PROCESS`) evidence as Gate 1's *primary* requirement, with relay-proxy mode only as a
  secondary, separately-labelled run (relay-proxy mode is structurally a sidecar-shaped
  network hop -- see `docs/feature-flags-research-gaps-v2.md` Thread 5 -- with a different
  cost profile). The relay-proxy numbers this harness produces satisfy only that secondary
  requirement; the primary (embedded-mode) evidence Gate 1 needs remains unproduced.
  This module's pinned GOFF provider version, `0.4.3`, has no embedded/`IN_PROCESS`
  evaluation mode in its public API at all: every evaluation is an HTTP round trip to a
  relay-proxy. That mode (`dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType`
  with `IN_PROCESS`/`REMOTE` constants) was verified to exist starting at provider `1.0.0`
  (checked by downloading `go-feature-flag-1.0.0.jar` and `go-feature-flag-1.2.0.jar` from
  Maven Central and inspecting them with `javap`/`unzip` -- see `GoffBenchmarkHarness`'s
  class javadoc for the detail).
  **Fixing this is not a one-line version bump -- it's a coordinated dependency-stack
  change.** Dependency resolution confirms provider `1.0.0` requires
  `dev.openfeature:sdk >= 1.16.0` (this module pins sdk `1.15.1`, below that floor), and
  provider `1.2.0` (latest) requires `sdk >= 1.21.0` -- so any provider bump forces an SDK
  bump in the same change. On top of that, `IN_PROCESS` mode at provider `1.0.0+` runs a
  bundled WASM evaluator via the Chicory WASM runtime (`com.dylibso.chicory:runtime`/`wasi`),
  a new runtime subsystem this module does not currently pull in -- not a lightweight
  addition. Whoever advances Slice B needs to either (a) bump this module's GOFF provider
  dependency to `>= 1.0.0`, bump `dev.openfeature:sdk` to `>= 1.16.0` (or `>= 1.21.0` for
  provider `1.2.0`) in the same change, add the Chicory WASM runtime dependency, and
  re-verify this harness against the newer API -- a genuine architectural decision, not a
  quick fix -- or (b) get the design spec's Gate 1 requirement explicitly revised to accept
  relay-proxy-only evidence. Only read the numbers this harness produces as relay-proxy
  numbers -- never as a stand-in for embedded-mode evidence, and never as Gate 1 sign-off.

## Running it

Requires Docker.

```bash
mvn -pl flags-benchmark-goff compile exec:java
```

## What it benchmarks

The raw OpenFeature Java `Client`, not the (not-yet-built) `GoffFlagEngine` production
adapter -- that adapter is Slice C, blocked on ratification. Gate 1's question is about
the *engine's* capacity; the facade's own "adds no I/O" property is a separate claim
already argued in Slice A and `feature-flags-research-gaps-v2.md` Thread 4.

## References

- `../docs/superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md`
- `../adr/0002-ratification-checklist.md`
