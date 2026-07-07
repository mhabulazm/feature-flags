package com.acme.flags.api;

public interface FeatureFlags {

    boolean isEnabled(FlagKey flag, FlagContext context);

    <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue, FlagContext context);

    <T> T getConfigValue(ConfigKey<T> key, FlagContext context);

    default boolean isEnabled(FlagKey flag) {
        return isEnabled(flag, FlagContext.current());
    }

    default <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue) {
        return getVariant(flag, type, defaultValue, FlagContext.current());
    }

    default <T> T getConfigValue(ConfigKey<T> key) {
        return getConfigValue(key, FlagContext.current());
    }
}
