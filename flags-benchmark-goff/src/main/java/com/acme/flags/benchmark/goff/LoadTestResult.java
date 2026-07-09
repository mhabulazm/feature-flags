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
