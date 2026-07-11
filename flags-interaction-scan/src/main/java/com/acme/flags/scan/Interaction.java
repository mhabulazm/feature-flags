package com.acme.flags.scan;

import java.util.List;

/** One interaction finding. Read-only data; carries category, the involved keys, its site, and a human detail. */
public record Interaction(Category category, List<String> keys, String site, String detail) {

    public enum Category {
        CO_REFERENCE, NESTING, CROSS_SERVICE
    }
}
