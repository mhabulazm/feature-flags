package com.acme.flags.api;

import com.acme.flags.spi.FlagEngine;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultFeatureFlags implements FeatureFlags {

    private static final Logger log = LoggerFactory.getLogger(DefaultFeatureFlags.class);

    private final FlagEngine engine;
    private final MeterRegistry metrics;

    public DefaultFeatureFlags(FlagEngine engine, MeterRegistry metrics) {
        this.engine = engine;
        this.metrics = metrics;
    }

    @Override
    public boolean isEnabled(FlagKey flag, FlagContext context) {
        boolean defaultValue = flag.metadata().defaultValue();
        boolean result;
        try {
            result = engine.evaluateBoolean(flag.key(), context.toMap(), defaultValue);
        } catch (RuntimeException e) {
            result = defaultValue;
            log.warn("Flag engine unavailable, falling back to default value for {}", flag.key(), e);
        }
        recordEvaluation(flag.key(), result);
        return result;
    }

    @Override
    public <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue, FlagContext context) {
        T result;
        try {
            result = engine.evaluateVariant(flag.key(), context.toMap(), type, defaultValue);
        } catch (RuntimeException e) {
            result = defaultValue;
            log.warn("Flag engine unavailable, falling back to default value for {}", flag.key(), e);
        }
        recordEvaluation(flag.key(), result);
        return result;
    }

    @Override
    public <T> T getConfigValue(ConfigKey<T> key, FlagContext context) {
        return getVariant(key, key.type(), key.defaultValue(), context);
    }

    private void recordEvaluation(String flagKey, Object result) {
        metrics.counter("feature_flag.evaluated", "flag", flagKey, "result", String.valueOf(result)).increment();
    }
}
