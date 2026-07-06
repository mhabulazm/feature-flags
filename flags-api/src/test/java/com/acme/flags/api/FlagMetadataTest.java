package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FlagMetadataTest {

    @Test
    void builder_buildsMetadataWithAllFieldsSet() {
        FlagMetadata metadata = FlagMetadata.builder()
                .owner("checkout-team")
                .ticket("CHECKOUT-1421")
                .defaultValue(false)
                .failureSemantics(FailureSemantics.FAIL_CLOSED)
                .expiresAfter(Duration.ofDays(90))
                .build();

        assertThat(metadata.owner()).isEqualTo("checkout-team");
        assertThat(metadata.ticket()).isEqualTo("CHECKOUT-1421");
        assertThat(metadata.defaultValue()).isFalse();
        assertThat(metadata.failureSemantics()).isEqualTo(FailureSemantics.FAIL_CLOSED);
        assertThat(metadata.expiresAfter()).isEqualTo(Duration.ofDays(90));
    }

    @Test
    void builder_allowsNullExpiresAfter_forPermanentConfig() {
        FlagMetadata metadata = FlagMetadata.builder()
                .owner("billing-team")
                .ticket("BILL-88")
                .defaultValue(true)
                .failureSemantics(FailureSemantics.FAIL_OPEN)
                .build();

        assertThat(metadata.expiresAfter()).isNull();
    }

    @Test
    void constructor_rejectsNullOwner() {
        assertThatThrownBy(() -> new FlagMetadata(null, "T-1", false, FailureSemantics.FAIL_OPEN, null))
                .isInstanceOf(NullPointerException.class);
    }
}
