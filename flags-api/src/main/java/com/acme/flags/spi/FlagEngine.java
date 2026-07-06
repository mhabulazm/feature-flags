package com.acme.flags.spi;

import java.util.Map;

public interface FlagEngine {

    boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue);

    <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue);
}
