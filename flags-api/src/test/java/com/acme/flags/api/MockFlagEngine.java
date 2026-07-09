package com.acme.flags.api;

import com.acme.flags.spi.FlagEngine;
import com.acme.flags.spi.FlagEngineUnavailableException;
import java.util.Map;

final class MockFlagEngine implements FlagEngine {

    private RuntimeException exceptionToThrow;
    private Boolean fixedBooleanResult;
    private Object fixedVariantResult;

    void throwOnEvaluate() {
        this.exceptionToThrow = new FlagEngineUnavailableException("engine unreachable");
    }

    void throwOnEvaluate(RuntimeException exception) {
        this.exceptionToThrow = exception;
    }

    void returnBoolean(boolean value) {
        this.fixedBooleanResult = value;
    }

    void returnVariant(Object value) {
        this.fixedVariantResult = value;
    }

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        return fixedBooleanResult != null ? fixedBooleanResult : defaultValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        return fixedVariantResult != null ? (T) fixedVariantResult : defaultValue;
    }
}
