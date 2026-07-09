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
