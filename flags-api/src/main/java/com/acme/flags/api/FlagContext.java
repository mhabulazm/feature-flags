package com.acme.flags.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FlagContext {

    private static final FlagContext ANONYMOUS = new FlagContext(null, null, Map.of());

    private final String tenantId;
    private final String userId;
    private final Map<String, String> traits;

    private FlagContext(String tenantId, String userId, Map<String, String> traits) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.traits = Map.copyOf(traits);
    }

    public static FlagContext forTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return new FlagContext(tenantId, null, Map.of());
    }

    public static FlagContext anonymous() {
        return ANONYMOUS;
    }

    public static FlagContext current() {
        FlagContext held = FlagContextHolder.get();
        return held != null ? held : ANONYMOUS;
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(traits);
        if (tenantId != null) {
            map.put("tenantId", tenantId);
        }
        if (userId != null) {
            map.put("userId", userId);
        }
        return Map.copyOf(map);
    }
}
