package com.acme.flags.testfixtures;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.flags.api.FailureSemantics;
import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagKey;
import com.acme.flags.api.FlagMetadata;
import org.junit.jupiter.api.Test;

class FakeFeatureFlagsTest {

    private static final FlagKey BOOL_FLAG = new TestFlagKey("test.bool-flag",
            FlagMetadata.builder()
                    .owner("test-team")
                    .ticket("TEST-1")
                    .defaultValue(false)
                    .failureSemantics(FailureSemantics.FAIL_CLOSED)
                    .build());

    @Test
    void isEnabled_returnsMetadataDefault_whenNotOverridden() {
        FakeFeatureFlags flags = new FakeFeatureFlags();

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous())).isFalse();
    }

    @Test
    void isEnabled_returnsOverriddenValue_whenSet() {
        FakeFeatureFlags flags = new FakeFeatureFlags();
        flags.set(BOOL_FLAG, true);

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous())).isTrue();
    }

    @Test
    void getVariant_returnsSuppliedDefault_whenNotOverridden() {
        FakeFeatureFlags flags = new FakeFeatureFlags();

        String result = flags.getVariant(BOOL_FLAG, String.class, "fallback", FlagContext.anonymous());

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void getVariant_returnsOverriddenValue_whenSet() {
        FakeFeatureFlags flags = new FakeFeatureFlags();
        flags.set(BOOL_FLAG, "override-value");

        String result = flags.getVariant(BOOL_FLAG, String.class, "fallback", FlagContext.anonymous());

        assertThat(result).isEqualTo("override-value");
    }

    private record TestFlagKey(String key, FlagMetadata metadata) implements FlagKey {
    }
}
