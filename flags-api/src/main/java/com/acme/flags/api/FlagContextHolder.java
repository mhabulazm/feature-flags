package com.acme.flags.api;

public final class FlagContextHolder {

    private static final ThreadLocal<FlagContext> CURRENT = new ThreadLocal<>();

    private FlagContextHolder() {
    }

    public static void set(FlagContext context) {
        CURRENT.set(context);
    }

    public static FlagContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
