package com.acme.flags.noop;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound once by Spring at {@code ApplicationContext} startup from whatever
 * {@code PropertySource} supplies {@code flags.overrides.*} (a plain properties file,
 * environment variables, or an AWS Parameter Store config-import) -- {@link #getOverrides()}
 * is a plain in-memory map read with no I/O in the call path. If a future change adds
 * {@code @RefreshScope} to this class to support live-reloading, that "no I/O on the hot
 * path" property needs re-verifying -- see
 * docs/superpowers/specs/2026-07-09-flags-v1-slice-b-ratification-evidence-design.md.
 */
@ConfigurationProperties(prefix = "flags")
public class FlagOverridesProperties {

    private final Map<String, String> overrides = new LinkedHashMap<>();

    public Map<String, String> getOverrides() {
        return overrides;
    }
}
