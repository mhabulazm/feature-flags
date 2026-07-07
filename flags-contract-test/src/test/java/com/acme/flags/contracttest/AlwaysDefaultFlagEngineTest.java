package com.acme.flags.contracttest;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

class AlwaysDefaultFlagEngineTest extends FlagEngineContractTest {

    @Override
    protected FlagEngine engine() {
        return new FlagEngine() {
            @Override
            public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
                return defaultValue;
            }

            @Override
            public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
                return defaultValue;
            }
        };
    }
}
