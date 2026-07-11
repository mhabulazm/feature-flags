# flags-benchmark-goff (Gate 1 ratification evidence)

Standalone load-test harness producing the peak-load evidence [ADR 0002's ratification
checklist](../adr/0002-ratification-checklist.md) Gate 1 asks for. Not a published
`flags-*` artifact -- deliberately excluded from `flags-bom`; nothing in the facade or
its consumers depends on this module.

## Status

Compiles cleanly against the real `dev.openfeature:sdk:1.21.0` and
`dev.openfeature.contrib.providers:go-feature-flag:1.2.0` dependencies pinned in this module's
`pom.xml` (verified via `mvn -pl flags-benchmark-goff clean compile`). The Chicory WASM runtime
that in-process evaluation needs arrives **transitively** via the provider -- confirmed with
`mvn dependency:tree`, so no explicit Chicory dependency is declared here. Benchmarks **both**
deployment modes: embedded/`IN_PROCESS` (Gate 1's primary evidence) and relay-proxy/`REMOTE`
(secondary). **Has never actually been run** -- running `GoffBenchmarkHarness.main()` needs a
Docker daemon (for the Testcontainers-managed `gofeatureflag/go-feature-flag:v1.55.0`
relay-proxy), which isn't available in the environment this was built in. Treat any numbers it
produces as unverified until someone with Docker access runs it.

## What it does

Runs a rate-limited load test against a real GOFF instance (relay-proxy started via
Testcontainers, pinned to image tag `v1.55.0` rather than `:latest` so runs taken weeks apart
are still comparable) across four throughput tiers (100 / 1,000 / 10,000 / 50,000 req/s, 10
seconds each), reporting p50/p99/p999 added latency and error rate per tier. It sweeps **both**
deployment modes, labelled separately:

1. **Embedded / `IN_PROCESS` (primary Gate 1 evidence).** The provider fetches the flag
   configuration from the relay-proxy once at startup and polls it for changes; each evaluation
   then runs locally through the bundled WASM evaluator (Chicory), with no per-evaluation network
   hop. This is the mode the checklist's "already-request-scoped / cached state, not a
   per-evaluation network call" condition actually describes.
2. **Relay-proxy / `REMOTE` (secondary).** Every evaluation is an HTTP round trip to the
   relay-proxy -- structurally a sidecar-shaped network hop (see
   `../docs/feature-flags-research-gaps-v2.md` Thread 5), with a different cost profile.

Per the Slice B spec's Gate 1 gaps:
- **No real peak req/s target exists in this repo.** The four tiers above are a sweep, not a
  guess at the real number -- read the real target off the curve at the ratification meeting
  instead of trusting a single pass/fail run.
- **The driver's own worker count caps the top tiers.** `RateLimitedLoadDriver` uses a
  closed-loop pool of 8 worker threads. Against network-latency-bound calls (relay-proxy mode), 8
  threads cannot actually sustain the 10,000 or 50,000 req/s tiers -- at those tiers the reported
  number reflects the driver's own saturation ceiling, not a confirmed sustained rate. In-process
  mode evaluates locally and is far faster per call, so its ceiling sits higher, but the same
  caveat applies: read the curve, not a single tier.
- **The embedded-mode blocker is resolved; what remains is running it.** The earlier pin
  (`go-feature-flag:0.4.3`) had no in-process mode at all -- `EvaluationType` only exists from
  provider `1.0.0`+ -- so the harness could produce relay-proxy/`REMOTE` numbers only, leaving
  Gate 1's *primary* (embedded) evidence unproducible. That is now fixed by a coordinated
  dependency-stack bump: provider `0.4.3 -> 1.2.0`, `dev.openfeature:sdk 1.15.1 -> 1.21.0` (forced
  -- provider `1.2.0` declares `sdk:[1.21.0,...)`), and the Chicory WASM runtime pulled
  transitively. The harness now wires the embedded sweep as its primary run. **What remains for
  Gate 1 sign-off is executing it** -- producing real numbers on a Docker-equipped machine and
  confirming the `v1.55.0` relay-proxy exposes the flag-configuration endpoint the in-process
  provider polls. Until that run happens, Gate 1 is *capable* of being evidenced but has no
  evidence yet.

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
