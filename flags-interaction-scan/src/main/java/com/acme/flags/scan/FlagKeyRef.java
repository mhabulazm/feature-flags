package com.acme.flags.scan;

/**
 * One flag key discovered in a registry enum: the declaring enum + constant, its key string, and its
 * namespace (the prefix before the first '.', e.g. "billing" from "billing.rate-limit-override").
 */
public record FlagKeyRef(String enumName, String constant, String key, String namespace) {
}
