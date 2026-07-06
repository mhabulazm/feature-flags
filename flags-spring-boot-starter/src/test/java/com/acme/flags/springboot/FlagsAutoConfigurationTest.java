package com.acme.flags.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.flags.api.FeatureFlags;
import com.acme.flags.noop.InMemoryFlagEngine;
import com.acme.flags.noop.NoOpFlagEngine;
import com.acme.flags.spi.FlagEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlagsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FlagsAutoConfiguration.class));

    @Test
    void defaultsToNoOpEngineAndProvidesFeatureFlagsBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FeatureFlags.class);
            assertThat(context).hasSingleBean(FlagEngine.class);
            assertThat(context.getBean(FlagEngine.class)).isInstanceOf(NoOpFlagEngine.class);
        });
    }

    @Test
    void wiresInMemoryEngine_whenPropertySet() {
        contextRunner
                .withPropertyValues("flags.engine=in-memory", "flags.overrides.checkout.new-checkout-flow=true")
                .run(context -> {
                    assertThat(context.getBean(FlagEngine.class)).isInstanceOf(InMemoryFlagEngine.class);
                    assertThat(context).hasSingleBean(FeatureFlags.class);
                });
    }
}
