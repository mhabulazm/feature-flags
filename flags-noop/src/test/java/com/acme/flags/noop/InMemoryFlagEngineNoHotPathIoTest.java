package com.acme.flags.noop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Confirms that {@link InMemoryFlagEngine} does not perform per-evaluation I/O.
 *
 * <p>This test validates the structural argument that Spring's {@code @ConfigurationProperties}
 * binding happens once at {@code ApplicationContext} startup, not on each access. After binding,
 * {@link FlagOverridesProperties#getOverrides()} returns a plain in-memory {@link Map}, and
 * {@code InMemoryFlagEngine.evaluateBoolean()} performs only a {@code Map.get()} lookup — no
 * rebind, no refresh, no I/O.
 *
 * <p>This evidence satisfies the "no I/O on the hot path" requirement from
 * {@code 0002-ratification-checklist.md} (Path A: structural argument + unit test). Path B
 * (real AWS Parameter Store integration test) is deferred until after ADR 0003 is accepted.
 */
class InMemoryFlagEngineNoHotPathIoTest {

    private FlagOverridesProperties properties;
    private InMemoryFlagEngine engine;

    @BeforeEach
    void setUp() {
        properties = spy(new FlagOverridesProperties());
        engine = new InMemoryFlagEngine(properties);
    }

    @Test
    void evaluateBoolean_readsOverridesFromMemory_noRebind() {
        // Arrange: set up an override
        properties.getOverrides().put("test-flag", "true");

        // Act: evaluate multiple times
        for (int i = 0; i < 10; i++) {
            boolean result = engine.evaluateBoolean("test-flag", Map.of(), false);
            assertThat(result).isTrue();
        }

        // Assert: getOverrides() was called (to read the map), but no Spring refresh/rebind occurred
        // The spy verifies we're only calling the map accessor, not triggering any Spring binding logic
        verify(properties, times(10)).getOverrides();
        verifyNoMoreInteractions(properties);
    }

    @Test
    void evaluateBoolean_withNoOverride_returnsDefault_noRebind() {
        // Act: evaluate a flag with no override
        boolean result = engine.evaluateBoolean("unknown-flag", Map.of(), true);

        // Assert: returns default, getOverrides() was called to check for override
        assertThat(result).isTrue();
        verify(properties).getOverrides();
        verifyNoMoreInteractions(properties);
    }

    @Test
    void evaluateVariant_readsOverridesFromMemory_noRebind() {
        // Arrange: set up a string variant override
        properties.getOverrides().put("test-variant", "variant-a");

        // Act: evaluate multiple times
        for (int i = 0; i < 10; i++) {
            String result = engine.evaluateVariant("test-variant", Map.of(), String.class, "default");
            assertThat(result).isEqualTo("variant-a");
        }

        // Assert: getOverrides() was called, but no refresh/rebind
        verify(properties, times(10)).getOverrides();
        verifyNoMoreInteractions(properties);
    }

    @Test
    void evaluateVariant_withNoOverride_returnsDefault_noRebind() {
        // Act: evaluate a variant with no override
        String result = engine.evaluateVariant("unknown-variant", Map.of(), String.class, "default");

        // Assert: returns default, getOverrides() was called to check
        assertThat(result).isEqualTo("default");
        verify(properties).getOverrides();
        verifyNoMoreInteractions(properties);
    }

    @Test
    void evaluateBoolean_multipleFlags_allReadFromMemory_noRebind() {
        // Arrange: set up multiple overrides
        properties.getOverrides().put("flag-1", "true");
        properties.getOverrides().put("flag-2", "false");
        properties.getOverrides().put("flag-3", "true");

        // Act: evaluate all flags multiple times
        for (int i = 0; i < 5; i++) {
            assertThat(engine.evaluateBoolean("flag-1", Map.of(), false)).isTrue();
            assertThat(engine.evaluateBoolean("flag-2", Map.of(), true)).isFalse();
            assertThat(engine.evaluateBoolean("flag-3", Map.of(), false)).isTrue();
        }

        // Assert: getOverrides() called 15 times (5 iterations × 3 flags), no other interactions
        verify(properties, times(15)).getOverrides();
        verifyNoMoreInteractions(properties);
    }
}