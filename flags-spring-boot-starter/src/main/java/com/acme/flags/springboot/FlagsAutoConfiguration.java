package com.acme.flags.springboot;

import com.acme.flags.api.DefaultFeatureFlags;
import com.acme.flags.api.FeatureFlags;
import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagContextResolver;
import com.acme.flags.noop.FlagOverridesProperties;
import com.acme.flags.noop.InMemoryFlagEngine;
import com.acme.flags.noop.NoOpFlagEngine;
import com.acme.flags.spi.FlagEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@EnableConfigurationProperties(FlagOverridesProperties.class)
public class FlagsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry flagsMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(FlagEngine.class)
    @ConditionalOnProperty(name = "flags.engine", havingValue = "in-memory")
    public FlagEngine inMemoryFlagEngine(FlagOverridesProperties properties) {
        return new InMemoryFlagEngine(properties);
    }

    @Bean
    @ConditionalOnMissingBean(FlagEngine.class)
    @ConditionalOnProperty(name = "flags.engine", havingValue = "noop", matchIfMissing = true)
    public FlagEngine noOpFlagEngine() {
        return new NoOpFlagEngine();
    }

    @Bean
    @ConditionalOnMissingBean(FeatureFlags.class)
    public FeatureFlags featureFlags(FlagEngine engine, MeterRegistry metrics) {
        return new DefaultFeatureFlags(engine, metrics);
    }

    @Bean
    @ConditionalOnMissingBean(FlagContextResolver.class)
    public FlagContextResolver anonymousFlagContextResolver() {
        return FlagContext::anonymous;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<FlagContextFilter> flagContextFilterRegistration(FlagContextResolver resolver) {
        FilterRegistrationBean<FlagContextFilter> registration =
                new FilterRegistrationBean<>(new FlagContextFilter(resolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
