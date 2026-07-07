package com.acme.flags.contracttest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.acme.flags.spi.FlagEngine;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public abstract class FlagEngineContractTest {

    protected abstract FlagEngine engine();

    @Test
    public void unknownKeyReturnsDefault() {
        assertThat(engine().evaluateBoolean("does-not-exist", Map.of(), true)).isTrue();
        assertThat(engine().evaluateBoolean("does-not-exist", Map.of(), false)).isFalse();
    }

    @Test
    public void nullContextTreatedAsEmpty() {
        assertThatCode(() -> engine().evaluateBoolean("any-key", null, false)).doesNotThrowAnyException();
    }

    @Test
    public void concurrentEvaluationIsSafe() throws Exception {
        FlagEngine target = engine();
        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Boolean>>mapToObj(i -> () -> target.evaluateBoolean("concurrent-key", Map.of(), false))
                    .toList();
            List<Future<Boolean>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            for (Future<Boolean> future : futures) {
                assertThat(future.get()).isFalse();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
