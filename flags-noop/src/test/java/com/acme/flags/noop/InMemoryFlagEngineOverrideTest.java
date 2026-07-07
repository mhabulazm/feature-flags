package com.acme.flags.noop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryFlagEngineOverrideTest {

    @Test
    void evaluateBoolean_returnsConfiguredOverride_whenPresent() {
        FlagOverridesProperties properties = new FlagOverridesProperties();
        properties.getOverrides().put("checkout.new-checkout-flow", "true");
        InMemoryFlagEngine engine = new InMemoryFlagEngine(properties);

        assertThat(engine.evaluateBoolean("checkout.new-checkout-flow", Map.of(), false)).isTrue();
    }

    @Test
    void evaluateVariant_parsesStringOverrideToRequestedType() {
        FlagOverridesProperties properties = new FlagOverridesProperties();
        properties.getOverrides().put("billing.max-retries", "5");
        InMemoryFlagEngine engine = new InMemoryFlagEngine(properties);

        Integer result = engine.evaluateVariant("billing.max-retries", Map.of(), Integer.class, 3);

        assertThat(result).isEqualTo(5);
    }
}
