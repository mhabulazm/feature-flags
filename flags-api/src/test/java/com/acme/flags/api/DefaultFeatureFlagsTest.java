package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultFeatureFlagsTest {

    private static final FlagKey BOOL_FLAG = new TestFlagKey("test.bool-flag",
            FlagMetadata.builder()
                    .owner("test-team")
                    .ticket("TEST-1")
                    .defaultValue(false)
                    .failureSemantics(FailureSemantics.FAIL_CLOSED)
                    .expiresAfter(Duration.ofDays(90))
                    .build());

    private MockFlagEngine engine;
    private SimpleMeterRegistry metrics;
    private DefaultFeatureFlags flags;

    @BeforeEach
    void setUp() {
        engine = new MockFlagEngine();
        metrics = new SimpleMeterRegistry();
        flags = new DefaultFeatureFlags(engine, metrics);
    }

    @Test
    void isEnabled_returnsEngineResult_whenEngineSucceeds() {
        engine.returnBoolean(true);

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous())).isTrue();
    }

    @Test
    void isEnabled_fallsBackToMetadataDefault_whenEngineThrows() {
        engine.throwOnEvaluate();

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous()))
                .isEqualTo(BOOL_FLAG.metadata().defaultValue());
    }

    @Test
    void isEnabled_recordsMetric_withMatchedOutcome_whenEngineSucceeds() {
        engine.returnBoolean(true);

        flags.isEnabled(BOOL_FLAG, FlagContext.anonymous());

        double count = metrics.counter("feature_flag.evaluated", "flag", BOOL_FLAG.key(), "outcome", "matched").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void isEnabled_recordsMetric_withFallbackOutcome_whenEngineThrows() {
        engine.throwOnEvaluate();

        flags.isEnabled(BOOL_FLAG, FlagContext.anonymous());

        double count = metrics.counter("feature_flag.evaluated", "flag", BOOL_FLAG.key(), "outcome", "fallback").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void getVariant_fallsBackToSuppliedDefault_whenEngineThrows() {
        engine.throwOnEvaluate();

        String result = flags.getVariant(BOOL_FLAG, String.class, "fallback", FlagContext.anonymous());

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void getVariant_recordsMetric_withoutTaggingRawValue() {
        engine.returnVariant("some-arbitrary-value");

        flags.getVariant(BOOL_FLAG, String.class, "default", FlagContext.anonymous());

        double matchedCount =
                metrics.counter("feature_flag.evaluated", "flag", BOOL_FLAG.key(), "outcome", "matched").count();
        assertThat(matchedCount).isEqualTo(1.0);
        assertThat(metrics.find("feature_flag.evaluated").tag("result", "some-arbitrary-value").counter()).isNull();
    }

    @Test
    void isEnabled_noContextOverload_resolvesCurrentContext() {
        engine.returnBoolean(true);

        assertThat(flags.isEnabled(BOOL_FLAG)).isTrue();
    }

    @Test
    void isEnabled_propagatesException_whenEngineThrowsNonTransportError() {
        engine.throwOnEvaluate(new IllegalStateException("adapter bug"));

        assertThatThrownBy(() -> flags.isEnabled(BOOL_FLAG, FlagContext.anonymous()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("adapter bug");
    }

    private record TestFlagKey(String key, FlagMetadata metadata) implements FlagKey {
    }
}
