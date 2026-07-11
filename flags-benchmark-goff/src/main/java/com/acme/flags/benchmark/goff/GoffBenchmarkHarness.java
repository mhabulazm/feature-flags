package com.acme.flags.benchmark.goff;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Gate 1 (ADR 0002 ratification checklist) peak-load evidence harness.
 *
 * <p><strong>Status: written and compiles, not yet executed.</strong> This harness compiles against
 * the real OpenFeature/GOFF dependencies but has never been run against a live GOFF instance --
 * running {@code main()} needs a Docker daemon for the Testcontainers-managed relay-proxy, which is
 * not available in the environment it was built in. Treat any numbers it would produce as
 * unverified until someone with Docker actually runs it. See the module README for how.
 *
 * <p>Benchmarks the raw OpenFeature Java SDK/{@link Client} directly -- not the not-yet-built
 * {@code GoffFlagEngine} production adapter (Slice C, blocked on ratification). Gate 1's question is
 * about the engine's capacity, not the facade's overhead; the facade's own "no I/O" property is a
 * separate, already-argued claim (Slice A and feature-flags-research-gaps-v2.md Thread 4), not
 * something this harness needs to re-prove.
 *
 * <h2>Two deployment modes, per the Slice B spec</h2>
 *
 * <p>The design spec requires deployment mode to be explicit and singular per run, with
 * embedded/library mode as the <em>primary</em> Gate 1 evidence and relay-proxy mode as a
 * secondary, separately-labelled run. This harness runs both, in that order, each bound to its own
 * OpenFeature domain so the two providers stay independent:
 * <ul>
 *   <li><strong>Embedded / {@link EvaluationType#IN_PROCESS} (primary).</strong> The provider
 *       fetches the flag configuration from the relay-proxy once at startup and polls it for
 *       changes (every {@link #FLAG_CONFIG_POLL_INTERVAL_MILLIS} ms); each evaluation then runs
 *       locally through the bundled WASM evaluator on the Chicory runtime, with <em>no
 *       per-evaluation network hop</em>. This is the mode the checklist's "already-request-scoped /
 *       cached state, not a per-evaluation network call" condition actually describes, and the mode
 *       GOFF's Tier-1 in-process story rests on.</li>
 *   <li><strong>Relay-proxy / {@link EvaluationType#REMOTE} (secondary).</strong> Every evaluation
 *       is an HTTP round trip to the relay-proxy -- structurally a sidecar-shaped network hop (see
 *       feature-flags-research-gaps-v2.md Thread 5, literature-quantified at up to 269% higher
 *       latency than in-process), a categorically different cost profile that must be labelled
 *       distinctly rather than left to stand in for the embedded numbers.</li>
 * </ul>
 *
 * <h2>Dependency stack (the embedded-mode blocker is now resolved)</h2>
 *
 * <p>An earlier pass pinned provider {@code go-feature-flag:0.4.3}, whose public API had no
 * in-process mode at all -- {@code bean.EvaluationType} did not exist before provider {@code 1.0.0},
 * so every evaluation was an HTTP round trip and Gate 1's <em>primary</em> (embedded) evidence was
 * structurally unproducible. Resolving that is not a one-line version bump (which is why it was
 * called out as its own task); it took a coordinated dependency-stack change:
 * <ul>
 *   <li>{@code go-feature-flag} provider {@code 0.4.3 -> 1.2.0} (adds
 *       {@code bean.EvaluationType.IN_PROCESS}).</li>
 *   <li>{@code dev.openfeature:sdk 1.15.1 -> 1.21.0} -- forced, not optional: provider {@code 1.2.0}
 *       declares {@code sdk:[1.21.0,...)}.</li>
 *   <li>The Chicory WASM runtime ({@code com.dylibso.chicory:wasi} plus {@code runtime}/{@code wasm}/
 *       {@code log} at {@code 1.7.5}) arrives <em>transitively</em> via the provider -- confirmed by
 *       {@code mvn dependency:tree}, so no explicit Chicory dependency is declared in this module's
 *       {@code pom.xml}.</li>
 * </ul>
 *
 * <p><strong>Still open -- needs Docker (the run this pass could not do):</strong> actually
 * executing both sweeps to produce numbers, and confirming the pinned relay-proxy image
 * ({@code v1.55.0}) exposes the flag-configuration endpoint the in-process provider polls at
 * startup. Neither is possible without a Docker daemon; this pass delivered the code and the
 * dependency bump only, re-verified by compilation, not execution.
 *
 * <h2>Request timeout</h2>
 *
 * <p>{@code GoFeatureFlagProviderOptions.builder().timeout(int)} is set explicitly to
 * {@link #REQUEST_TIMEOUT_MILLIS}. In relay-proxy/{@code REMOTE} mode this bounds the per-evaluation
 * HTTP call: the value feeds OkHttp's {@code connectTimeout/readTimeout/callTimeout/writeTimeout}
 * (default 10,000ms if left unset). This addresses Task 4's review finding -- {@code
 * RateLimitedLoadDriver}'s worker threads call {@code Thread.interrupt()} on shutdown, which cannot
 * forcibly unblock a thread stuck in a raw blocking socket read; OkHttp's {@code callTimeout}
 * enforces the deadline itself, independent of interruption, so a hung request fails fast instead of
 * parking a worker for the rest of the run.
 *
 * <h2>Error-rate measurement: why the action calls {@code getBooleanDetails}, not {@code
 * getBooleanValue}</h2>
 *
 * <p>{@code RateLimitedLoadDriver} counts an evaluation as an error only when the action {@code
 * Runnable} throws a {@code RuntimeException}. {@code client.getBooleanValue(...)} never throws on a
 * provider/timeout error -- it silently returns the default value -- so using it as the action would
 * peg the error-rate column at ~0% even under total GOFF failure. This was bytecode-verified against
 * the originally-pinned {@code sdk-1.15.1} and re-confirmed against the now-pinned {@code sdk-1.21.0}
 * during the dependency bump above: {@code getBooleanValue(String, Boolean, EvaluationContext)} is
 * just {@code getBooleanDetails(...).getValue()} (no error handling of its own), and the private
 * {@code evaluateFlag(...)} it delegates through catches every {@code java.lang.Exception} raised
 * during provider evaluation, enriching the returned {@link FlagEvaluationDetails} with the default
 * value and an {@code ErrorCode} rather than letting anything propagate. No exception ever leaves
 * {@code getBooleanValue}.
 *
 * <p>So the action instead calls {@link Client#getBooleanDetails(String, Boolean, EvaluationContext)}
 * and inspects {@link FlagEvaluationDetails#getErrorCode()} -- {@code null} on a normal evaluation,
 * non-null whenever {@code evaluateFlag} swallowed an exception internally. When it is non-null the
 * action throws, which is exactly what {@code RateLimitedLoadDriver.runWorker}'s {@code catch
 * (RuntimeException)} already watches for. This uses only core {@code dev.openfeature:sdk} API and is
 * independent of the deployment mode being measured.
 */
public final class GoffBenchmarkHarness {

    private static final int[] RATE_TIERS_PER_SECOND = {100, 1_000, 10_000, 50_000};
    private static final int DURATION_SECONDS_PER_TIER = 10;
    private static final String FLAG_KEY = "benchmark-flag";
    private static final int RELAY_PROXY_PORT = 1031;
    private static final int REQUEST_TIMEOUT_MILLIS = 3_000;

    /**
     * How often in-process mode polls the relay-proxy for flag-configuration changes. This affects
     * config freshness only, not per-evaluation latency -- once a config is loaded, in-process
     * evaluation runs locally via WASM with no network call, so this interval never sits on the hot
     * path being measured.
     */
    private static final long FLAG_CONFIG_POLL_INTERVAL_MILLIS = 5_000L;

    /**
     * Pinned relay-proxy server image tag. Verified as a real, current release (tagged {@code
     * v1.55.0}, released 2026-07-02). Deliberately not {@code :latest} -- this harness exists to
     * produce comparable latency numbers across runs, and {@code :latest} would let the server build
     * silently drift between runs taken weeks apart.
     */
    private static final String RELAY_PROXY_IMAGE = "gofeatureflag/go-feature-flag:v1.55.0";

    public static void main(String[] args) {
        try (GenericContainer<?> relayProxy = startRelayProxy()) {
            String endpoint = "http://" + relayProxy.getHost() + ":" + relayProxy.getMappedPort(RELAY_PROXY_PORT);
            try {
                System.out.println("=== Embedded mode (IN_PROCESS evaluation) -- Gate 1 PRIMARY evidence ===");
                System.out.println("Flag config is fetched from the relay-proxy at startup and polled for changes;");
                System.out.println("each evaluation runs locally via the bundled WASM evaluator (Chicory), with no");
                System.out.println("per-evaluation network hop -- the mode Gate 1's pass criterion actually describes.");
                runSweep("embedded", endpoint, EvaluationType.IN_PROCESS);

                System.out.println();
                System.out.println("=== Relay-proxy mode (REMOTE evaluation) -- Gate 1 SECONDARY evidence ===");
                System.out.println("Every evaluation is an HTTP round trip to the relay-proxy -- a sidecar-shaped");
                System.out.println("network hop (see feature-flags-research-gaps-v2.md Thread 5), a different cost");
                System.out.println("profile. Run and labelled separately; not a substitute for the embedded numbers.");
                runSweep("relay-proxy", endpoint, EvaluationType.REMOTE);
            } finally {
                OpenFeatureAPI.getInstance().shutdown();
            }
        }
    }

    private static GenericContainer<?> startRelayProxy() {
        GenericContainer<?> container = new GenericContainer<>(RELAY_PROXY_IMAGE)
                .withExposedPorts(RELAY_PROXY_PORT)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("goff-proxy.yaml"), "/goff/goff-proxy.yaml")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("flags.yaml"), "/goff/flags.yaml")
                .waitingFor(Wait.forHttp("/health").forPort(RELAY_PROXY_PORT));
        container.start();
        return container;
    }

    private static void runSweep(String mode, String endpoint, EvaluationType evaluationType) {
        Client client = buildClient(mode, endpoint, evaluationType);
        RateLimitedLoadDriver driver = new RateLimitedLoadDriver();
        EvaluationContext context = new ImmutableContext("benchmark-user");

        for (int rate : RATE_TIERS_PER_SECOND) {
            LoadTestResult result = driver.run(
                    mode + " @ " + rate + " req/s",
                    rate,
                    DURATION_SECONDS_PER_TIER,
                    () -> evaluateOrThrow(client, context));
            System.out.println(result);
        }
    }

    /**
     * Evaluates the benchmark flag and throws a {@code RuntimeException} on provider/timeout error so
     * that {@code RateLimitedLoadDriver}'s error counter actually fires. See the class javadoc's
     * "Error-rate measurement" section for why this uses {@code getBooleanDetails} rather than
     * {@code getBooleanValue}: the latter swallows all evaluation errors into a default-valued result
     * and never throws, which would leave the error-rate metric permanently at ~0%.
     */
    private static void evaluateOrThrow(Client client, EvaluationContext context) {
        FlagEvaluationDetails<Boolean> details = client.getBooleanDetails(FLAG_KEY, false, context);
        if (details.getErrorCode() != null) {
            throw new RuntimeException(
                    "GOFF evaluation error: " + details.getErrorCode() + " (" + details.getErrorMessage() + ")");
        }
    }

    private static Client buildClient(String domain, String endpoint, EvaluationType evaluationType) {
        var builder = GoFeatureFlagProviderOptions.builder()
                .endpoint(endpoint)
                .evaluationType(evaluationType)
                .timeout(REQUEST_TIMEOUT_MILLIS);
        if (evaluationType == EvaluationType.IN_PROCESS) {
            builder.flagChangePollingIntervalMs(FLAG_CONFIG_POLL_INTERVAL_MILLIS);
        }
        GoFeatureFlagProviderOptions options = builder.build();

        FeatureProvider provider;
        try {
            provider = new GoFeatureFlagProvider(options);
        } catch (InvalidOptions e) {
            throw new IllegalStateException(
                    "GOFF provider rejected options for endpoint " + endpoint + " (mode=" + domain + ")", e);
        }

        // Bind each mode to its own OpenFeature domain so the embedded and relay-proxy sweeps use
        // independent providers instead of clobbering a single global default provider.
        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(domain, provider);
        return api.getClient(domain);
    }

    private GoffBenchmarkHarness() {
    }
}
