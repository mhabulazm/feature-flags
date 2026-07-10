package com.acme.flags.noop;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

/**
 * In-memory {@link FlagEngine} implementation backed by {@link FlagOverridesProperties}.
 *
 * <p>This engine serves as a bridge mode for static flag overrides before a real engine (GOFF/Unleash)
 * is available. Overrides are sourced from Spring properties ({@code flags.overrides.<key>}) which may
 * be configured via properties files, environment variables, or AWS Parameter Store.
 *
 * <p><strong>Hot-path guarantee:</strong> This engine performs no per-evaluation I/O. Spring's
 * {@code @ConfigurationProperties} binding happens once at {@code ApplicationContext} startup.
 * After binding, {@link FlagOverridesProperties#getOverrides()} returns an in-memory {@link Map},
 * and {@code evaluateBoolean()}/{@code evaluateVariant()} perform only {@code Map.get()} lookups.
 *
 * <p><strong>WARNING:</strong> Do NOT add {@code @RefreshScope} to {@link FlagOverridesProperties}.
 * That would introduce periodic I/O and violate the "no I/O on hot path" contract from
 * ADR 0002 B3. If live reload of overrides is needed, use a different mechanism that does not
 * rebind on the evaluation path.
 */
public final class InMemoryFlagEngine implements FlagEngine {

    private final FlagOverridesProperties properties;

    public InMemoryFlagEngine(FlagOverridesProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        String override = properties.getOverrides().get(key);
        return override == null ? defaultValue : Boolean.parseBoolean(override);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        String override = properties.getOverrides().get(key);
        if (override == null) {
            return defaultValue;
        }
        if (type == String.class) {
            return (T) override;
        }
        if (type == Boolean.class) {
            return (T) Boolean.valueOf(override);
        }
        if (type == Integer.class) {
            return (T) Integer.valueOf(override);
        }
        if (type == Long.class) {
            return (T) Long.valueOf(override);
        }
        if (type == Double.class) {
            return (T) Double.valueOf(override);
        }
        return defaultValue;
    }
}
