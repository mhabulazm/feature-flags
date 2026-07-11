package com.acme.flags.scan;

import java.util.Set;

/** A flag-key use at a call site, with the method + if-guard context the detector needs. */
public record FlagReference(
        FlagKeyRef flagKey,
        String file,
        int line,
        String methodId,
        String owningNamespace,
        Set<String> guardKeys) {
}
