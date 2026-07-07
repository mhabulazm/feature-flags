package com.acme.flags.api;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

final class MockFlagEngine implements FlagEngine {

    private boolean throwOnEvaluate = false;
    private Boolean fixedBooleanResult;
    private Object fixedVariantResult;

    void throwOnEvaluate() {
        this.throwOnEvaluate = true;
    }

    void returnBoolean(boolean value) {
        this.fixedBooleanResult = value;
    }

    void returnVariant(Object value) {
        this.fixedVariantResult = value;
    }

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        if (throwOnEvaluate) {
            throw new RuntimeException("engine unreachable");
        }
        return fixedBooleanResult != null ? fixedBooleanResult : defaultValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        if (throwOnEvaluate) {
            throw new RuntimeException("engine unreachable");
        }
        return fixedVariantResult != null ? (T) fixedVariantResult : defaultValue;
    }
}
