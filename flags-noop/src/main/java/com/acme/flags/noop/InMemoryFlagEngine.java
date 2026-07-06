package com.acme.flags.noop;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

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
