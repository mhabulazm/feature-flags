package com.acme.flags.noop;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flags")
public class FlagOverridesProperties {

    private final Map<String, String> overrides = new LinkedHashMap<>();

    public Map<String, String> getOverrides() {
        return overrides;
    }
}
