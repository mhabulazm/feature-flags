package com.acme.flags.noop;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

public final class NoOpFlagEngine implements FlagEngine {

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        return defaultValue;
    }
}
