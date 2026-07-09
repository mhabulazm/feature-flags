# v1 Slice B — Ratification Evidence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce the evidence [ADR 0002's ratification checklist](../../../adr/0002-ratification-checklist.md) needs: a Parameter Store caching proof (fully verifiable now), a Gate 1 peak-load benchmark harness (compiles against real dependencies, cannot be *run* in this environment — no Docker), and a Gate 2 self-serve runbook (not code — a human-executable script and recording template).

**Architecture:** Three independent pieces, no shared files. (1) A new test + doc-comment in `flags-noop` proving `InMemoryFlagEngine` never touches I/O per evaluation. (2) A new, deliberately unpublished Maven module `flags-benchmark-goff` — real `dev.openfeature:sdk` + GOFF provider + Testcontainers dependencies, kept out of `flags-bom` — with pure-logic pieces (percentile math, rate-limited driver) fully unit-tested without Docker, and a `main()`-method harness that needs Docker to actually run. (3) A markdown runbook for the Gate 2 human exercise.

**Tech Stack:** Java 21, JUnit 5 + AssertJ (no Mockito), Maven, `dev.openfeature:sdk:1.15.1`, `dev.openfeature.contrib.providers:go-feature-flag:0.4.3`, `org.testcontainers:testcontainers-bom:2.0.5`.

## Global Constraints

- No Mockito or any new mocking framework anywhere in this plan.
- `flags-benchmark-goff` is NOT added to `flags-bom`'s dependency management — it is not a published, consumer-facing artifact.
- `flags-benchmark-goff`'s tests (`LoadTestResultTest`, `RateLimitedLoadDriverTest`) must never require Docker or a network call — they exercise pure logic and a fake in-process workload only. `GoffBenchmarkHarness` (the Docker-dependent orchestration) is a `public static void main` class, NOT a `@Test` class, specifically so `mvn test`/`mvn verify` never attempts to start a container.
- Task 5's code (`GoffBenchmarkHarness` and its supporting classes) is written against real external library documentation, not a codebase I've already verified — treat class/method names as a well-researched starting point, not a guaranteed-correct transcription. If `mvn compile` reveals a name mismatch against the real downloaded JARs, fix it based on the actual API (check the decompiled class or IDE navigation) and document what was corrected.
- Commit messages: no `Co-Authored-By: Claude` trailer.
- Package for the new module: `com.acme.flags.benchmark.goff` (deliberately separate from `com.acme.flags.engine.goff`, to keep this benchmarking-only code visually and structurally distinct from the eventual production adapter).

---

### Task 1: Parameter Store caching verification

**Files:**
- Modify: `flags-noop/src/main/java/com/acme/flags/noop/FlagOverridesProperties.java`
- Create: `flags-noop/src/test/java/com/acme/flags/noop/InMemoryFlagEngineNoIoOnHotPathTest.java`

**Interfaces:** none new — this only adds a doc comment and a test against the existing `InMemoryFlagEngine`/`FlagOverridesProperties`.

- [ ] **Step 1: Write the test**

```java
package com.acme.flags.noop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryFlagEngineNoIoOnHotPathTest {

    @Test
    void evaluateBoolean_manyCalls_completeAtInMemorySpeed_notNetworkSpeed() {
        FlagOverridesProperties properties = new FlagOverridesProperties();
        properties.getOverrides().put("checkout.new-checkout-flow", "true");
        InMemoryFlagEngine engine = new InMemoryFlagEngine(properties);

        long start = System.nanoTime();
        for (int i = 0; i < 100_000; i++) {
            engine.evaluateBoolean("checkout.new-checkout-flow", Map.of(), false);
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        // 100,000 calls at network/AWS-API latency (single-digit ms each, per the
        // Flagsmith remote-eval figures in feature-flags-comparison.md) would take
        // minutes. A structural/timing argument, not a mock-call-counter: see
        // docs/superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md
        // for why -- FlagOverridesProperties is bound once by Spring at startup
        // regardless of the underlying PropertySource (plain file, env vars, or a
        // Parameter Store config-import), so evaluateBoolean's only per-call work is
        // a Map.get() against that already-bound map. Bound generously (2s for 100k
        // calls) for CI safety margin -- still >1000x tighter than one round trip
        // of network I/O would allow for the same call count.
        assertThat(elapsedMillis).isLessThan(2000);
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn -pl flags-noop test -Dtest=InMemoryFlagEngineNoIoOnHotPathTest`
Expected: PASS (this isn't classic TDD RED/GREEN since there's no code change to drive — it's a characterization test proving an existing property. Confirm it passes, and confirm it would plausibly fail if `evaluateBoolean` did a real I/O call, by inspection: 100,000 calls at even 1ms each would be 100 seconds, far past the 2-second bound.)

- [ ] **Step 3: Add the doc-comment warning to `FlagOverridesProperties`**

Replace the file's contents:

```java
package com.acme.flags.noop;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound once by Spring at {@code ApplicationContext} startup from whatever
 * {@code PropertySource} supplies {@code flags.overrides.*} (a plain properties file,
 * environment variables, or an AWS Parameter Store config-import) -- {@link #getOverrides()}
 * is a plain in-memory map read with no I/O in the call path. If a future change adds
 * {@code @RefreshScope} to this class to support live-reloading, that "no I/O on the hot
 * path" property needs re-verifying -- see
 * docs/superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md.
 */
@ConfigurationProperties(prefix = "flags")
public class FlagOverridesProperties {

    private final Map<String, String> overrides = new LinkedHashMap<>();

    public Map<String, String> getOverrides() {
        return overrides;
    }
}
```

- [ ] **Step 4: Run the full flags-noop suite to confirm no regression**

Run: `mvn -pl flags-noop test`
Expected: PASS, all tests including the pre-existing `InMemoryFlagEngineTest`/`InMemoryFlagEngineOverrideTest`/`NoOpFlagEngineTest`.

- [ ] **Step 5: Commit**

```bash
git add flags-noop/src/main/java/com/acme/flags/noop/FlagOverridesProperties.java flags-noop/src/test/java/com/acme/flags/noop/InMemoryFlagEngineNoIoOnHotPathTest.java
git commit -m "Add Parameter Store caching evidence: InMemoryFlagEngine has no I/O on the hot path"
```

---

### Task 2: Scaffold the `flags-benchmark-goff` module

**Files:**
- Modify: `pom.xml` (root)
- Create: `flags-benchmark-goff/pom.xml`

**Interfaces:** Produces the module skeleton later tasks add source to.

- [ ] **Step 1: Add the module to the root reactor**

In `pom.xml`, add to the `<modules>` list (after `<module>flags-engine-goff</module>`):

```xml
        <module>flags-benchmark-goff</module>
```

- [ ] **Step 2: Create the module's pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.acme.flags</groupId>
        <artifactId>flags-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>flags-benchmark-goff</artifactId>
    <packaging>jar</packaging>
    <name>Flags GOFF Ratification Benchmark (v1 Slice B, Gate 1)</name>
    <description>Standalone load-test harness gathering peak-load evidence for ADR 0002's ratification checklist Gate 1. NOT a published flags-* artifact -- deliberately excluded from flags-bom. Requires Docker to run; see README.</description>

    <properties>
        <openfeature-sdk.version>1.15.1</openfeature-sdk.version>
        <goff-provider.version>0.4.3</goff-provider.version>
        <testcontainers.version>2.0.5</testcontainers.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.testcontainers</groupId>
                <artifactId>testcontainers-bom</artifactId>
                <version>${testcontainers.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>dev.openfeature</groupId>
            <artifactId>sdk</artifactId>
            <version>${openfeature-sdk.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.openfeature.contrib.providers</groupId>
            <artifactId>go-feature-flag</artifactId>
            <version>${goff-provider.version}</version>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>com.acme.flags.benchmark.goff.GoffBenchmarkHarness</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 3: Verify the (empty) module resolves and builds**

Run: `mvn -pl flags-benchmark-goff -am compile`
Expected: BUILD SUCCESS. This has no source files yet, so this step's real purpose is confirming the new dependencies (`dev.openfeature:sdk:1.15.1`, `dev.openfeature.contrib.providers:go-feature-flag:0.4.3`, `org.testcontainers:testcontainers` via the `2.0.5` BOM) resolve from Maven Central without a version conflict. If any coordinate is wrong or unresolvable, stop and report — don't guess a replacement version silently.

- [ ] **Step 4: Commit**

```bash
git add pom.xml flags-benchmark-goff/pom.xml
git commit -m "Scaffold flags-benchmark-goff module (not published via flags-bom)"
```

---

### Task 3: `LoadTestResult` — percentile computation

**Files:**
- Create: `flags-benchmark-goff/src/main/java/com/acme/flags/benchmark/goff/LoadTestResult.java`
- Test: `flags-benchmark-goff/src/test/java/com/acme/flags/benchmark/goff/LoadTestResultTest.java`

**Interfaces:**
- Produces: `LoadTestResult.from(String label, long[] latenciesNanos, long errorCount): LoadTestResult`, a record with `label()`, `totalCalls()`, `errorCount()`, `p50Nanos()`, `p99Nanos()`, `p999Nanos()`, `errorRate()`, and a human-readable `toString()`. Consumed by Task 4 (`RateLimitedLoadDriver`) and Task 5 (`GoffBenchmarkHarness`).

- [ ] **Step 1: Write the failing tests**

```java
package com.acme.flags.benchmark.goff;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoadTestResultTest {

    @Test
    void from_computesPercentiles_fromKnownLatencies() {
        // 100 values: 1ms, 2ms, ..., 100ms (in nanos), so p50=50ms, p99=99ms, p999=100ms
        long[] latencies = new long[100];
        for (int i = 0; i < 100; i++) {
            latencies[i] = (i + 1) * 1_000_000L;
        }

        LoadTestResult result = LoadTestResult.from("test", latencies, 0);

        assertThat(result.totalCalls()).isEqualTo(100);
        assertThat(result.p50Nanos()).isEqualTo(50_000_000L);
        assertThat(result.p99Nanos()).isEqualTo(99_000_000L);
        assertThat(result.p999Nanos()).isEqualTo(100_000_000L);
    }

    @Test
    void from_computesErrorRate() {
        long[] latencies = new long[]{1_000_000L, 2_000_000L};

        LoadTestResult result = LoadTestResult.from("test", latencies, 1);

        assertThat(result.errorCount()).isEqualTo(1);
        assertThat(result.errorRate()).isEqualTo(0.5);
    }

    @Test
    void from_handlesEmptyLatencies() {
        LoadTestResult result = LoadTestResult.from("test", new long[0], 0);

        assertThat(result.totalCalls()).isEqualTo(0);
        assertThat(result.p50Nanos()).isEqualTo(0);
        assertThat(result.errorRate()).isEqualTo(0.0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl flags-benchmark-goff test -Dtest=LoadTestResultTest`
Expected: FAIL — compilation error, `LoadTestResult` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.acme.flags.benchmark.goff;

import java.util.Arrays;

public record LoadTestResult(
        String label,
        long totalCalls,
        long errorCount,
        long p50Nanos,
        long p99Nanos,
        long p999Nanos
) {

    public static LoadTestResult from(String label, long[] latenciesNanos, long errorCount) {
        long[] sorted = latenciesNanos.clone();
        Arrays.sort(sorted);
        return new LoadTestResult(
                label,
                sorted.length,
                errorCount,
                percentile(sorted, 0.50),
                percentile(sorted, 0.99),
                percentile(sorted, 0.999));
    }

    private static long percentile(long[] sortedLatenciesNanos, double percentile) {
        if (sortedLatenciesNanos.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sortedLatenciesNanos.length) - 1;
        int clampedIndex = Math.max(0, Math.min(index, sortedLatenciesNanos.length - 1));
        return sortedLatenciesNanos[clampedIndex];
    }

    public double errorRate() {
        return totalCalls == 0 ? 0.0 : (double) errorCount / totalCalls;
    }

    @Override
    public String toString() {
        return "%-40s calls=%-8d errors=%-6d (%.2f%%) p50=%.3fms p99=%.3fms p999=%.3fms".formatted(
                label, totalCalls, errorCount, errorRate() * 100,
                p50Nanos / 1_000_000.0, p99Nanos / 1_000_000.0, p999Nanos / 1_000_000.0);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl flags-benchmark-goff test -Dtest=LoadTestResultTest`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add flags-benchmark-goff/src/main/java/com/acme/flags/benchmark/goff/LoadTestResult.java flags-benchmark-goff/src/test/java/com/acme/flags/benchmark/goff/LoadTestResultTest.java
git commit -m "Add LoadTestResult: percentile computation for the Gate 1 harness"
```

---

### Task 4: `RateLimitedLoadDriver`

**Files:**
- Create: `flags-benchmark-goff/src/main/java/com/acme/flags/benchmark/goff/RateLimitedLoadDriver.java`
- Test: `flags-benchmark-goff/src/test/java/com/acme/flags/benchmark/goff/RateLimitedLoadDriverTest.java`

**Interfaces:**
- Consumes: `LoadTestResult.from(...)` (Task 3).
- Produces: `RateLimitedLoadDriver.run(String label, int targetRatePerSecond, int durationSeconds, Runnable action): LoadTestResult` — consumed by Task 5.

This class's tests use only a fake in-process `Runnable` (no Docker, no network) — it exercises the driver's own concurrency/timing/error-counting logic in isolation from the real GOFF-calling code Task 5 adds later.

- [ ] **Step 1: Write the failing tests**

```java
package com.acme.flags.benchmark.goff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RateLimitedLoadDriverTest {

    private final RateLimitedLoadDriver driver = new RateLimitedLoadDriver();

    @Test
    void run_approximatesTargetRate_forFastNoOpAction() {
        LoadTestResult result = driver.run("noop", 50, 1, () -> {
        });

        // Rate-limiting via sleep is inherently approximate; assert the driver is in
        // the right order of magnitude rather than pinning an exact count.
        assertThat(result.totalCalls()).isBetween(20L, 200L);
        assertThat(result.errorCount()).isZero();
    }

    @Test
    void run_countsErrors_whenActionThrows() {
        LoadTestResult result = driver.run("failing", 50, 1, () -> {
            throw new IllegalStateException("boom");
        });

        assertThat(result.totalCalls()).isGreaterThan(0);
        assertThat(result.errorCount()).isEqualTo(result.totalCalls());
    }

    @Test
    void run_recordsNonZeroLatency_forSlowAction() {
        AtomicInteger callCount = new AtomicInteger();
        LoadTestResult result = driver.run("slow", 10, 1, () -> {
            callCount.incrementAndGet();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThat(callCount.get()).isGreaterThan(0);
        assertThat(result.p50Nanos()).isGreaterThan(0);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl flags-benchmark-goff test -Dtest=RateLimitedLoadDriverTest`
Expected: FAIL — compilation error, `RateLimitedLoadDriver` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.acme.flags.benchmark.goff;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

public final class RateLimitedLoadDriver {

    private static final int WORKER_COUNT = 8;

    public LoadTestResult run(String label, int targetRatePerSecond, int durationSeconds, Runnable action) {
        ConcurrentLinkedQueue<Long> latenciesNanos = new ConcurrentLinkedQueue<>();
        AtomicLong errorCount = new AtomicLong();
        long intervalNanosPerWorker = TimeUnit.SECONDS.toNanos(1) * WORKER_COUNT / Math.max(targetRatePerSecond, 1);
        long endAtNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);

        ExecutorService pool = Executors.newFixedThreadPool(WORKER_COUNT);
        CountDownLatch latch = new CountDownLatch(WORKER_COUNT);
        for (int i = 0; i < WORKER_COUNT; i++) {
            pool.submit(() -> {
                try {
                    runWorker(action, endAtNanos, intervalNanosPerWorker, latenciesNanos, errorCount);
                } finally {
                    latch.countDown();
                }
            });
        }
        try {
            latch.await(durationSeconds + 30L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }

        long[] latenciesArray = latenciesNanos.stream().mapToLong(Long::longValue).toArray();
        return LoadTestResult.from(label, latenciesArray, errorCount.get());
    }

    private void runWorker(Runnable action, long endAtNanos, long intervalNanos,
            ConcurrentLinkedQueue<Long> latenciesNanos, AtomicLong errorCount) {
        while (System.nanoTime() < endAtNanos) {
            long callStart = System.nanoTime();
            try {
                action.run();
            } catch (RuntimeException e) {
                errorCount.incrementAndGet();
            }
            long elapsed = System.nanoTime() - callStart;
            latenciesNanos.add(elapsed);

            long sleepNanos = intervalNanos - elapsed;
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl flags-benchmark-goff test -Dtest=RateLimitedLoadDriverTest`
Expected: PASS, 3/3. (Tests run for ~1 second each due to the 1-second `durationSeconds` used in each test case — this is expected, not a hang.)

- [ ] **Step 5: Commit**

```bash
git add flags-benchmark-goff/src/main/java/com/acme/flags/benchmark/goff/RateLimitedLoadDriver.java flags-benchmark-goff/src/test/java/com/acme/flags/benchmark/goff/RateLimitedLoadDriverTest.java
git commit -m "Add RateLimitedLoadDriver: worker-pool rate-limited load generator"
```

---

### Task 5: `GoffBenchmarkHarness` + Testcontainers wiring + README

**Files:**
- Create: `flags-benchmark-goff/src/main/resources/goff-proxy.yaml`
- Create: `flags-benchmark-goff/src/main/resources/flags.yaml`
- Create: `flags-benchmark-goff/src/main/java/com/acme/flags/benchmark/goff/GoffBenchmarkHarness.java`
- Create: `flags-benchmark-goff/README.md`

**Interfaces:**
- Consumes: `RateLimitedLoadDriver.run(...)` (Task 4), `LoadTestResult` (Task 3).
- Produces: nothing consumed elsewhere — this is the top-level entry point.

**This task cannot be run end-to-end in this environment (no Docker) — see Global Constraints.** The goal is: compiles cleanly against the real dependencies, is internally consistent with Tasks 3-4's interfaces, and is honestly documented as unexecuted.

- [ ] **Step 1: Write the relay-proxy config fixture**

`flags-benchmark-goff/src/main/resources/goff-proxy.yaml`:

```yaml
retrievers:
  - kind: file
    path: /goff/flags.yaml
```

- [ ] **Step 2: Write the flag definition fixture**

`flags-benchmark-goff/src/main/resources/flags.yaml`:

```yaml
flags:
  benchmark-flag:
    variations:
      enabled: true
      disabled: false
    defaultVariation: disabled
    rules: []
```

- [ ] **Step 3: Write `GoffBenchmarkHarness`**

```java
package com.acme.flags.benchmark.goff;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Gate 1 (ADR 0002 ratification checklist) peak-load evidence harness.
 *
 * <p><strong>Status: written, not executed.</strong> This code compiles against the real
 * OpenFeature/GOFF dependencies, but has never actually been run against a live GOFF instance --
 * the environment this was built in has no Docker daemon available. Whoever picks up Slice B needs
 * Docker (for the Testcontainers-managed relay-proxy) to produce real numbers. See the module
 * README for how to run it.
 *
 * <p>Benchmarks the raw OpenFeature Java SDK/Client directly -- not the not-yet-built
 * {@code GoffFlagEngine} production adapter (that's Slice C, blocked on ratification). Gate 1's
 * question is about the engine's capacity, not the facade's overhead; the facade's own "no I/O"
 * property is a separate, already-argued claim (see Slice A and the Context Object pattern research
 * in feature-flags-research-gaps-v2.md Thread 4), not something this harness needs to re-prove.
 */
public final class GoffBenchmarkHarness {

    private static final int[] RATE_TIERS_PER_SECOND = {100, 1_000, 10_000, 50_000};
    private static final int DURATION_SECONDS_PER_TIER = 10;
    private static final String FLAG_KEY = "benchmark-flag";

    public static void main(String[] args) {
        try (GenericContainer<?> relayProxy = startRelayProxy()) {
            String endpoint = "http://" + relayProxy.getHost() + ":" + relayProxy.getMappedPort(1031);

            System.out.println("=== Embedded/library mode (IN_PROCESS evaluation) ===");
            runSweep(endpoint, GoFeatureFlagProviderOptions.EvaluationType.IN_PROCESS);

            System.out.println();
            System.out.println("=== Relay-proxy mode (REMOTE evaluation) ===");
            System.out.println("Only read these numbers if the team actually intends to use");
            System.out.println("relay-proxy mode for a real use case -- see the Slice B spec's");
            System.out.println("Gate 1 deployment-mode gap before treating this as equivalent");
            System.out.println("to the embedded-mode results above.");
            runSweep(endpoint, GoFeatureFlagProviderOptions.EvaluationType.REMOTE);
        }
    }

    private static GenericContainer<?> startRelayProxy() {
        GenericContainer<?> container = new GenericContainer<>("gofeatureflag/go-feature-flag:latest")
                .withExposedPorts(1031)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("goff-proxy.yaml"), "/goff/goff-proxy.yaml")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("flags.yaml"), "/goff/flags.yaml")
                .waitingFor(Wait.forHttp("/health").forPort(1031));
        container.start();
        return container;
    }

    private static void runSweep(String endpoint, GoFeatureFlagProviderOptions.EvaluationType mode) {
        Client client = buildClient(endpoint, mode);
        RateLimitedLoadDriver driver = new RateLimitedLoadDriver();
        EvaluationContext context = new ImmutableContext("benchmark-user");

        for (int rate : RATE_TIERS_PER_SECOND) {
            LoadTestResult result = driver.run(
                    mode + " @ " + rate + " req/s",
                    rate,
                    DURATION_SECONDS_PER_TIER,
                    () -> client.getBooleanValue(FLAG_KEY, false, context));
            System.out.println(result);
        }
    }

    private static Client buildClient(String endpoint, GoFeatureFlagProviderOptions.EvaluationType mode) {
        FeatureProvider provider = new GoFeatureFlagProvider(
                GoFeatureFlagProviderOptions.builder()
                        .endpoint(endpoint)
                        .evaluationType(mode)
                        .build());
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(provider);
        return api.getClient();
    }

    private GoffBenchmarkHarness() {
    }
}
```

**If this doesn't compile as written:** the class/method names above (`GoFeatureFlagProviderOptions.EvaluationType`, `ImmutableContext`, `.evaluationType(...)`, etc.) were sourced from the GOFF provider's public documentation, not verified against the actual downloaded jar's bytecode. If `mvn compile` reports an unresolved symbol, inspect the real API (e.g. `javap -public` against the jar in `~/.m2/repository/dev/openfeature/contrib/providers/go-feature-flag/0.4.3/`, or your IDE's navigation) and correct the code to match. Document what you changed and why in your report.

- [ ] **Step 4: Compile the module against the real dependencies**

Run: `mvn -pl flags-benchmark-goff -am compile`
Expected: BUILD SUCCESS. This is the actual verification for this task — it proves the harness is source-correct against the real OpenFeature SDK and GOFF provider APIs, even though nobody can run `main()` here.

- [ ] **Step 5: Run the module's test suite (must stay Docker-free)**

Run: `mvn -pl flags-benchmark-goff test`
Expected: PASS — only `LoadTestResultTest` (Task 3) and `RateLimitedLoadDriverTest` (Task 4) run; `GoffBenchmarkHarness` has no `@Test` methods, so nothing here attempts to start a container.

- [ ] **Step 6: Write the module README**

```markdown
# flags-benchmark-goff (Gate 1 ratification evidence)

Standalone load-test harness producing the peak-load evidence [ADR 0002's ratification
checklist](../adr/0002-ratification-checklist.md) Gate 1 asks for. Not a published
`flags-*` artifact -- deliberately excluded from `flags-bom`; nothing in the facade or
its consumers depends on this module.

## Status

Compiles cleanly against the real `dev.openfeature:sdk` and
`dev.openfeature.contrib.providers:go-feature-flag` dependencies (verified via `mvn compile`
in the environment this was built in). **Has never actually been run** -- running
`GoffBenchmarkHarness.main()` needs a Docker daemon (for the Testcontainers-managed
`gofeatureflag/go-feature-flag` relay-proxy), which wasn't available in that environment.
Treat the numbers this produces as unverified until someone with Docker access runs it.

## What it does

Runs a rate-limited load test against a real GOFF relay-proxy (started via Testcontainers)
across four throughput tiers (100 / 1,000 / 10,000 / 50,000 req/s, 10 seconds each) and two
deployment modes (embedded/`IN_PROCESS` and relay-proxy/`REMOTE`), reporting p50/p99/p999
added latency and error rate per tier per mode.

Per the Slice B spec's Gate 1 gaps:
- **No real peak req/s target exists in this repo.** The four tiers above are a sweep, not
  a guess at the real number -- read the real target off the curve at the ratification
  meeting instead of trusting a single pass/fail run.
- **Embedded and relay-proxy modes are reported separately, never conflated.** Relay-proxy
  mode is structurally a sidecar-shaped network hop (see
  `docs/feature-flags-research-gaps-v2.md` Thread 5) with a different cost profile. Only
  read the relay-proxy numbers if the team actually intends to use that deployment mode.

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
```

- [ ] **Step 7: Commit**

```bash
git add flags-benchmark-goff/src/main/resources/goff-proxy.yaml flags-benchmark-goff/src/main/resources/flags.yaml flags-benchmark-goff/src/main/java/com/acme/flags/benchmark/goff/GoffBenchmarkHarness.java flags-benchmark-goff/README.md
git commit -m "Add GoffBenchmarkHarness: Gate 1 load-test orchestration (compiles, unexecuted -- no Docker in this environment)"
```

---

### Task 6: Gate 2 self-serve runbook

**Files:**
- Create: `docs/feature-flags-v1-slice-b-gate2-runbook.md`

**Interfaces:** none (documentation only — no code).

- [ ] **Step 1: Write the runbook**

```markdown
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
```

- [ ] **Step 2: Commit**

```bash
git add docs/feature-flags-v1-slice-b-gate2-runbook.md
git commit -m "Add Gate 2 self-serve runbook (operational template, not code)"
```

---

### Task 7: Cross-reference and full-reactor verification

**Files:**
- Modify: `docs/superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md`

**Interfaces:** none (documentation + verification only).

- [ ] **Step 1: Add a status update to the Slice B spec**

Add this note directly under the `## Status` heading (before the existing paragraph):

```markdown
**Implementation status (this pass):** Parameter Store caching evidence is complete and
verified (`flags-noop`). Gate 1's harness (`flags-benchmark-goff`) compiles against the
real OpenFeature/GOFF dependencies but has never been run -- no Docker in the environment
this was built in; see that module's README. Gate 2's runbook
(`../../feature-flags-v1-slice-b-gate2-runbook.md`) is written but not yet executed with a
real non-engineer.
```

- [ ] **Step 2: Run the full reactor build**

Run: `mvn -B verify`
Expected: BUILD SUCCESS across all 9 modules (the original 8 plus `flags-benchmark-goff`). `flags-benchmark-goff`'s own test run (Task 5, Step 5) must stay Docker-free and green.

- [ ] **Step 3: Confirm `flags-bom` still excludes the benchmark module**

Run: `grep -c flags-benchmark-goff flags-bom/pom.xml`
Expected: `0` — confirming the deliberate exclusion from the published-artifact BOM held through the whole implementation.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md
git commit -m "Note Slice B implementation status in the spec"
```

## Self-Review Notes

- **Spec coverage:** all three Slice B requirements (Gate 1 harness, Gate 2 evidence, Parameter Store caching) map to tasks. The spec's explicit "out of scope" items (real AWS wiring, the actual peak req/s figure, the actual self-serve requirement) are correctly NOT built here — this plan produces the *mechanism* to gather evidence, not the evidence's real-world numbers, matching the spec's own framing.
- **Docker/Testcontainers honesty:** Task 5 is explicitly marked as compile-verified-only, not run-verified, both in the plan and in the shipped README/javadoc. No task claims Gate 1 evidence has actually been produced.
- **No Mockito:** every test in this plan uses either plain JUnit/AssertJ assertions or a hand-rolled fake (`Runnable`), consistent with the rest of the repo.
- **Type/interface consistency:** `LoadTestResult.from(...)` (Task 3) is used identically by `RateLimitedLoadDriverTest` (Task 4, via `driver.run(...)`'s return type) and `GoffBenchmarkHarness` (Task 5). `RateLimitedLoadDriver.run(...)`'s signature is threaded consistently from Task 4's definition into Task 5's call site.
