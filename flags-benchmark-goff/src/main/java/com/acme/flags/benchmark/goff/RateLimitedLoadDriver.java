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
        boolean completed = false;
        try {
            completed = latch.await(durationSeconds + 30L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.shutdownNow();
        }

        boolean terminated = false;
        try {
            terminated = pool.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!completed || !terminated) {
            System.err.println("RateLimitedLoadDriver: workers did not complete cleanly within the grace "
                    + "period for '" + label + "' -- results may be incomplete");
        }

        long[] latenciesArray = latenciesNanos.stream().mapToLong(Long::longValue).toArray();
        return LoadTestResult.from(label, latenciesArray, errorCount.get());
    }

    private void runWorker(Runnable action, long endAtNanos, long intervalNanos,
            ConcurrentLinkedQueue<Long> latenciesNanos, AtomicLong errorCount) {
        while (!Thread.currentThread().isInterrupted() && System.nanoTime() < endAtNanos) {
            long callStart = System.nanoTime();
            try {
                action.run();
            } catch (RuntimeException e) {
                errorCount.incrementAndGet();
            }
            long elapsed = System.nanoTime() - callStart;
            latenciesNanos.add(elapsed);

            long sleepNanos = intervalNanos - elapsed;
            long remainingUntilDeadline = endAtNanos - System.nanoTime();
            long actualSleepNanos = Math.min(sleepNanos, remainingUntilDeadline);
            if (actualSleepNanos > 0) {
                LockSupport.parkNanos(actualSleepNanos);
            }
        }
    }
}
