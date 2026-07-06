package com.acme.flags.testfixtures;

import com.acme.flags.api.ConfigKey;
import com.acme.flags.api.FeatureFlags;
import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagKey;
import java.util.HashMap;
import java.util.Map;

public final class FakeFeatureFlags implements FeatureFlags {

    private final Map<String, Object> overrides = new HashMap<>();

    public void set(FlagKey flag, boolean value) {
        overrides.put(flag.key(), value);
    }

    public <T> void set(FlagKey flag, T value) {
        overrides.put(flag.key(), value);
    }

    public void clear() {
        overrides.clear();
    }

    @Override
    public boolean isEnabled(FlagKey flag, FlagContext context) {
        Object override = overrides.get(flag.key());
        return override instanceof Boolean value ? value : flag.metadata().defaultValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue, FlagContext context) {
        Object override = overrides.get(flag.key());
        return type.isInstance(override) ? (T) override : defaultValue;
    }

    @Override
    public <T> T getConfigValue(ConfigKey<T> key, FlagContext context) {
        return getVariant(key, key.type(), key.defaultValue(), context);
    }
}
