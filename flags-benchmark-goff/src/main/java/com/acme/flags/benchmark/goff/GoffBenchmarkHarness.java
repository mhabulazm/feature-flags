package com.acme.flags.benchmark.goff;

import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProvider;
import dev.openfeature.contrib.providers.gofeatureflag.GoFeatureFlagProviderOptions;
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
 * <h2>Gate 1 blocker: no embedded/{@code IN_PROCESS} mode at this module's pinned provider version</h2>
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
 * primarily asks for. <strong>Gate 1 is therefore NOT satisfied by this module as it stands</strong> --
 * relay-proxy/{@code REMOTE} numbers are the secondary evidence the design spec allows, not a
 * substitute for the primary embedded-mode evidence it requires, and that primary evidence remains
 * unproduced. This is a blocker for Slice B / Gate 1 sign-off, not a minor caveat, and it is a real
 * gap for whoever advances Slice B, not a simplification made in this pass.
 *
 * <p>Bumping the GOFF provider to {@code >= 1.0.0} is <strong>not a one-line version change</strong>
 * -- it is a coordinated dependency-stack change. Dependency resolution confirms provider
 * {@code 1.0.0} requires {@code dev.openfeature:sdk >= 1.16.0} (this module pins sdk
 * {@code 1.15.1}, below that floor), and provider {@code 1.2.0} (latest) requires
 * {@code sdk >= 1.21.0}; either provider bump therefore forces an SDK bump in the same change.
 * Separately, {@code IN_PROCESS} mode at provider {@code 1.0.0+} works by executing a bundled WASM
 * evaluator through the Chicory WASM runtime ({@code com.dylibso.chicory:runtime}/{@code wasi}) --
 * a new runtime subsystem this module does not currently depend on, not a lightweight addition. So
 * the real remediation is: bump {@code dev.openfeature:sdk} to {@code >= 1.16.0} (or
 * {@code >= 1.21.0} for provider {@code 1.2.0}) together with the GOFF provider bump, plus add the
 * Chicory WASM runtime dependency, all in one coordinated change -- a genuine architectural decision
 * for whoever owns Slice B next, not a quick fix. The alternative is (b) accept relay-proxy-only
 * evidence and get the design spec's Gate 1 requirement explicitly revised to match. Neither
 * decision belongs in this task.
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
 * hung relay-proxy request fails fast internally instead of parking a worker thread for the rest of
 * the run. Whether that fast failure is actually visible to {@code RateLimitedLoadDriver} as a
 * counted error is a separate question -- see the next section.
 *
 * <h2>Error-rate measurement: why the action calls {@code getBooleanDetails}, not {@code
 * getBooleanValue}</h2>
 *
 * <p>{@code RateLimitedLoadDriver} only counts an evaluation as an error when the action
 * {@code Runnable} throws a {@code RuntimeException} (see {@code RateLimitedLoadDriver.runWorker}).
 * An earlier draft of this class called {@code client.getBooleanValue(FLAG_KEY, false, context)}
 * directly as the action, which never throws on provider/timeout errors -- it silently returns the
 * default value instead, so the error-rate column would always read ~0% even under total GOFF
 * failure. Verified by decompiling {@code dev.openfeature.sdk.OpenFeatureClient} from the pinned
 * {@code sdk-1.15.1.jar} with {@code javap -p -c}:
 *
 * <ul>
 *   <li>{@code getBooleanValue(String, Boolean, EvaluationContext)} bytecode is just an
 *       {@code invokevirtual} of {@code getBooleanDetails(...)} followed by a checkcast/unbox --
 *       {@code getBooleanValue} has no error handling of its own; it fully delegates to
 *       {@code getBooleanDetails}.</li>
 *   <li>{@code getBooleanDetails(...)} delegates in turn to the private {@code evaluateFlag(...)},
 *       whose {@code Exception table} entry {@code (34, 290) -> 309, Class java/lang/Exception} shows
 *       it catches every {@code Exception} thrown during provider evaluation (network errors,
 *       {@code ProviderNotReadyError}, OkHttp {@code callTimeout} failures, etc.) rather than letting
 *       any of them propagate.</li>
 *   <li>The catch handler (bytecode offsets 309-392) sets an {@code ErrorCode} on the
 *       {@code FlagEvaluationDetails} it is building -- {@code ErrorCode.GENERAL} for a plain
 *       exception, or the specific code from {@code OpenFeatureError.getErrorCode()} when the
 *       exception is one of those -- sets an error message, then calls the private
 *       {@code enrichDetailsWithErrorDefaults(T, FlagEvaluationDetails)} helper. That helper's own
 *       bytecode is just {@code setValue(defaultValue)} followed by
 *       {@code setReason(Reason.ERROR.toString())} -- i.e. it fills in the default value and marks
 *       the reason as {@code "ERROR"}, it does not throw. {@code evaluateFlag} then returns this
 *       enriched, non-null {@code FlagEvaluationDetails} object normally ({@code areturn} at offset
 *       432) -- no exception ever leaves {@code evaluateFlag}, {@code getBooleanDetails}, or
 *       {@code getBooleanValue}.</li>
 * </ul>
 *
 * <p>Net effect: {@code getBooleanValue} cannot be made to throw by any provider-side failure, so it
 * structurally cannot drive {@code RateLimitedLoadDriver}'s error counter. This class's action
 * therefore calls {@link Client#getBooleanDetails(String, Boolean, EvaluationContext)} instead and
 * inspects the returned {@link FlagEvaluationDetails#getErrorCode()}: per the decompilation above,
 * that field is {@code null} on a normal evaluation and non-null ({@code ErrorCode.GENERAL} or a more
 * specific code) whenever {@code evaluateFlag} swallowed an exception internally. When it is
 * non-null, the action throws a {@code RuntimeException} itself, which is exactly what
 * {@code RateLimitedLoadDriver.runWorker}'s existing {@code catch (RuntimeException)} is already
 * watching for -- restoring the "a hung/failed request is counted as an error" property the class
 * javadoc claimed but that {@code getBooleanValue} could never actually deliver. This uses only core
 * {@code dev.openfeature:sdk} API ({@code getBooleanDetails}, {@code FlagEvaluationDetails}) already
 * on this module's classpath -- it does not require the GOFF-provider-version bump documented above,
 * which is a separate, unrelated gap ({@code IN_PROCESS} evaluation mode).
 */
public final class GoffBenchmarkHarness {

    private static final int[] RATE_TIERS_PER_SECOND = {100, 1_000, 10_000, 50_000};
    private static final int DURATION_SECONDS_PER_TIER = 10;
    private static final String FLAG_KEY = "benchmark-flag";
    private static final int RELAY_PROXY_PORT = 1031;
    private static final int REQUEST_TIMEOUT_MILLIS = 3_000;

    /**
     * Pinned relay-proxy server image tag. Verified as a real, current release via the
     * {@code gofeatureflag/go-feature-flag} Docker Hub tag list and the
     * {@code thomaspoignant/go-feature-flag} GitHub releases page (tagged {@code v1.55.0}, released
     * 2026-07-02). Deliberately not {@code :latest} -- this harness exists to produce comparable
     * latency numbers across runs, and {@code :latest} would let the server build silently drift
     * between runs taken weeks apart.
     */
    private static final String RELAY_PROXY_IMAGE = "gofeatureflag/go-feature-flag:v1.55.0";

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

    private static void runSweep(String endpoint) {
        Client client = buildClient(endpoint);
        RateLimitedLoadDriver driver = new RateLimitedLoadDriver();
        EvaluationContext context = new ImmutableContext("benchmark-user");

        for (int rate : RATE_TIERS_PER_SECOND) {
            LoadTestResult result = driver.run(
                    "relay-proxy @ " + rate + " req/s",
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
