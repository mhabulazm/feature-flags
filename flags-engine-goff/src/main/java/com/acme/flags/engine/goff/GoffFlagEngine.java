package com.acme.flags.engine.goff;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

/**
 * Skeleton {@link FlagEngine} adapter for GO Feature Flag (GOFF) via the OpenFeature Java SDK.
 *
 * <p><strong>Status: pre-ratification skeleton.</strong> GOFF is the <em>recommended</em> engine in
 * ADR 0002 but is not yet ratified, and ADR 0002 blocks implementation start until it is. This class
 * defines the adapter's shape without pulling in the OpenFeature/GOFF dependency or implementing
 * evaluation. Both methods throw {@link UnsupportedOperationException}, which — unlike
 * {@link com.acme.flags.spi.FlagEngineUnavailableException} — is <strong>not</strong> caught by
 * {@code DefaultFeatureFlags}'s narrowed fallback handling, so calling this skeleton live would
 * propagate a real exception rather than silently falling back. Nothing wires it live today:
 * {@code FlagsAutoConfiguration} has no {@code goff} branch yet.
 *
 * <p>To complete on ratification: add the {@code dev.openfeature:sdk} + GO Feature Flag provider
 * dependencies (see {@code pom.xml}), inject a configured OpenFeature {@code Client}, implement the
 * mappings below, and remove the {@code @Disabled} annotation from {@code GoffFlagEngineContractTest}.
 * See {@code flags-engine-goff/README.md} for the full checklist.
 */
public final class GoffFlagEngine implements FlagEngine {

    private static final String NOT_WIRED =
            "GOFF adapter not wired — ADR 0002 not yet ratified (see flags-engine-goff/README.md)";

    // On ratification, hold the configured OpenFeature client here:
    //
    //   private final dev.openfeature.sdk.Client client;
    //
    //   public GoffFlagEngine(dev.openfeature.sdk.Client client) {
    //       this.client = client;
    //   }
    //
    // The client is created once, from a GO Feature Flag FeatureProvider pointed at the relay/proxy:
    //
    //   OpenFeatureAPI api = OpenFeatureAPI.getInstance();
    //   api.setProviderAndWait(new GoFeatureFlagProvider(options));
    //   Client client = api.getClient();

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        // TODO(ADR-0002): return client.getBooleanValue(key, defaultValue, toEvaluationContext(context));
        throw new UnsupportedOperationException(NOT_WIRED);
    }

    @Override
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        // TODO(ADR-0002): dispatch on `type` to the matching OpenFeature typed getter, e.g.
        //   var ctx = toEvaluationContext(context);
        //   if (type == String.class)  return type.cast(client.getStringValue(key, (String) defaultValue, ctx));
        //   if (type == Boolean.class) return type.cast(client.getBooleanValue(key, (Boolean) defaultValue, ctx));
        //   if (type == Integer.class) return type.cast(client.getIntegerValue(key, (Integer) defaultValue, ctx));
        //   if (type == Double.class)  return type.cast(client.getDoubleValue(key, (Double) defaultValue, ctx));
        //   otherwise map client.getObjectValue(key, Value.objectToValue(defaultValue), ctx) back to T.
        throw new UnsupportedOperationException(NOT_WIRED);
    }

    // On ratification, translate the facade's flat trait map into an OpenFeature EvaluationContext:
    //
    //   private static EvaluationContext toEvaluationContext(Map<String, String> context) {
    //       Map<String, String> traits = context == null ? Map.of() : context;
    //       // FlagContext puts tenantId/userId/traits into this map; pick a stable targeting key.
    //       String targetingKey = traits.getOrDefault("userId", traits.getOrDefault("tenantId", ""));
    //       Map<String, Value> attrs = new HashMap<>();
    //       traits.forEach((k, v) -> attrs.put(k, new Value(v)));
    //       return new ImmutableContext(targetingKey, attrs);
    //   }
}
