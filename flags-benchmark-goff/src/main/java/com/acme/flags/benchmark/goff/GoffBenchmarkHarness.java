package com.acme.flags.benchmark.goff;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
import dev.openfeature.contrib.providers.gofeatureflag.exception.InvalidOptions;
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
 *
 * <h2>Known gap: no embedded/{@code IN_PROCESS} mode at this module's pinned provider version</h2>
 *
 * <p>The design spec (Gate 1, "deployment mode must be explicit and singular per run") calls for
 * embedded/library-mode evidence as the <em>primary</em> thing Gate 1 needs, with relay-proxy mode
 * only as a secondary, separately-labelled run. This module pins
 * {@code dev.openfeature.contrib.providers:go-feature-flag:0.4.3} (see the module {@code pom.xml},
 * Task 2), and that version's public API has no in-process evaluation mode at all -- every
 * evaluation is an HTTP round trip to a GOFF relay-proxy via
 * {@code GoFeatureFlagController.evaluateFlag(...)}. Verified two ways:
 * <ul>
 *   <li>{@code javap -public} against the pinned 0.4.3 jar shows
 *       {@code GoFeatureFlagProviderOptionsBuilder} has no {@code evaluationType(...)} method, and
 *       {@code unzip -l} of that jar has no {@code EvaluationType} class anywhere in it.</li>
 *   <li>Downloading {@code go-feature-flag:1.2.0} (the current latest, per Maven Central's
 *       {@code maven-metadata.xml}) and running the same {@code javap}/{@code unzip} commands shows
 *       {@code dev.openfeature.contrib.providers.gofeatureflag.bean.EvaluationType} <em>does</em>
 *       exist there, with exactly the {@code IN_PROCESS}/{@code REMOTE} constants the design spec
 *       and this class's earlier draft assumed. Bisecting further shows the class is already present
 *       in provider {@code 1.0.0} -- i.e. the feature was added somewhere in the {@code 0.4.x -> 1.0.0}
 *       jump, after the version this module pins.</li>
 * </ul>
 *
 * <p>Consequence: this harness currently produces <strong>relay-proxy/{@code REMOTE}-mode numbers
 * only</strong>. It does not, and as written cannot, produce the embedded-mode evidence Gate 1
 * primarily asks for. This is a real gap for whoever advances Slice B, not a simplification made in
 * this pass -- the options are (a) bump this module's GOFF provider dependency to {@code >= 1.0.0}
 * and re-verify this harness's API usage against the newer jar (bytecode-verified above to expose
 * {@code .evaluationType(EvaluationType.IN_PROCESS)}), or (b) accept relay-proxy-only evidence and
 * get the design spec's Gate 1 requirement explicitly revised to match. Neither decision belongs in
 * this task.
 *
 * <h2>Request timeout</h2>
 *
 * <p>{@code GoFeatureFlagProviderOptionsBuilder.timeout(int)} is set explicitly to
 * {@link #REQUEST_TIMEOUT_MILLIS}. Confirmed via {@code javap -c} decompilation of
 * {@code GoFeatureFlagController}'s constructor that this value feeds
 * {@code OkHttpClient.Builder.connectTimeout/readTimeout/callTimeout/writeTimeout(...)}, all in
 * {@code TimeUnit.MILLISECONDS} (default 10,000ms if left unset/zero). This directly addresses Task
 * 4's review finding: {@code RateLimitedLoadDriver}'s worker threads call {@code Thread.interrupt()}
 * on shutdown, which cannot forcibly unblock a thread stuck inside a raw blocking socket read.
 * OkHttp's {@code callTimeout} enforces the deadline itself, independent of thread interruption, so a
 * hung relay-proxy request fails fast with an exception (counted as an error by
 * {@code RateLimitedLoadDriver}) instead of parking a worker thread for the rest of the run.
 */
public final class GoffBenchmarkHarness {

    private static final int[] RATE_TIERS_PER_SECOND = {100, 1_000, 10_000, 50_000};
    private static final int DURATION_SECONDS_PER_TIER = 10;
    private static final String FLAG_KEY = "benchmark-flag";
    private static final int RELAY_PROXY_PORT = 1031;
    private static final int REQUEST_TIMEOUT_MILLIS = 3_000;

    public static void main(String[] args) {
        try (GenericContainer<?> relayProxy = startRelayProxy()) {
            String endpoint = "http://" + relayProxy.getHost() + ":" + relayProxy.getMappedPort(RELAY_PROXY_PORT);

            System.out.println("=== Relay-proxy mode (REMOTE evaluation) ===");
            System.out.println("This module's pinned GOFF provider (0.4.3) has no embedded/IN_PROCESS");
            System.out.println("evaluation mode -- see GoffBenchmarkHarness's class javadoc and the module");
            System.out.println("README for why, and for what the design spec's Gate 1 embedded-mode");
            System.out.println("requirement still needs before it can be satisfied.");
            runSweep(endpoint);
        }
    }

    private static GenericContainer<?> startRelayProxy() {
        GenericContainer<?> container = new GenericContainer<>("gofeatureflag/go-feature-flag:latest")
                .withExposedPorts(RELAY_PROXY_PORT)
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("goff-proxy.yaml"), "/goff/goff-proxy.yaml")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("flags.yaml"), "/goff/flags.yaml")
                .waitingFor(Wait.forHttp("/health").forPort(RELAY_PROXY_PORT));
        container.start();
        return container;
    }

    private static void runSweep(String endpoint) {
        Client client = buildClient(endpoint);
        RateLimitedLoadDriver driver = new RateLimitedLoadDriver();
        EvaluationContext context = new ImmutableContext("benchmark-user");

        for (int rate : RATE_TIERS_PER_SECOND) {
            LoadTestResult result = driver.run(
                    "relay-proxy @ " + rate + " req/s",
                    rate,
                    DURATION_SECONDS_PER_TIER,
                    () -> client.getBooleanValue(FLAG_KEY, false, context));
            System.out.println(result);
        }
    }

    private static Client buildClient(String endpoint) {
        GoFeatureFlagProviderOptions options = GoFeatureFlagProviderOptions.builder()
                .endpoint(endpoint)
                .timeout(REQUEST_TIMEOUT_MILLIS)
                .build();

        FeatureProvider provider;
        try {
            provider = new GoFeatureFlagProvider(options);
        } catch (InvalidOptions e) {
            throw new IllegalStateException("GOFF provider rejected options for endpoint " + endpoint, e);
        }

        OpenFeatureAPI api = OpenFeatureAPI.getInstance();
        api.setProviderAndWait(provider);
        return api.getClient();
    }

    private GoffBenchmarkHarness() {
    }
}
