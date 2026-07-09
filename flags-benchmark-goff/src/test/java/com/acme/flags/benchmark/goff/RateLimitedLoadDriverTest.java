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
