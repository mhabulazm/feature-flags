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
