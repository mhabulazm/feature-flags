package com.acme.flags.api;

public interface ConfigKey<T> extends FlagKey {
    Class<T> type();

    T defaultValue();
}
