package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlagContextHolderTest {

    @AfterEach
    void clear() {
        FlagContextHolder.clear();
    }

    @Test
    void get_returnsNull_whenNothingSet() {
        assertThat(FlagContextHolder.get()).isNull();
    }

    @Test
    void get_returnsSetContext() {
        FlagContext context = FlagContext.forTenant("tenant-1");

        FlagContextHolder.set(context);

        assertThat(FlagContextHolder.get()).isEqualTo(context);
    }

    @Test
    void clear_removesSetContext() {
        FlagContextHolder.set(FlagContext.forTenant("tenant-1"));

        FlagContextHolder.clear();

        assertThat(FlagContextHolder.get()).isNull();
    }
}
