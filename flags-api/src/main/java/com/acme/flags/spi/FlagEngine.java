package com.acme.flags.spi;

import java.util.Map;

/**
 * Contract every flag-evaluation engine must satisfy.
 *
 * <p>MUST return {@code defaultValue} for an unknown/unrecognized key — never throw for "flag
 * doesn't exist." MUST throw {@link FlagEngineUnavailableException} — and only that type — for
 * transport/connectivity failure; any other {@link RuntimeException} an implementation throws is
 * treated by {@code DefaultFeatureFlags} as a bug, not an outage, and propagates uncaught. MUST
 * treat a {@code null} or empty context map as equivalent to "no targeting context available," not
 * as an error. MUST be safe for concurrent calls from multiple threads.
 */
public interface FlagEngine {

    boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue);

    <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue);
}
