package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FlagContextTest {

    @Test
    void anonymous_hasNoTenantOrUserAndEmptyTraits() {
        FlagContext context = FlagContext.anonymous();

        assertThat(context.tenantId()).isNull();
        assertThat(context.userId()).isNull();
        assertThat(context.toMap()).isEmpty();
    }

    @Test
    void forTenant_populatesTenantIdInMap() {
        FlagContext context = FlagContext.forTenant("tenant-42");

        assertThat(context.tenantId()).isEqualTo("tenant-42");
        assertThat(context.toMap()).isEqualTo(Map.of("tenantId", "tenant-42"));
    }

    @Test
    void forTenant_rejectsNullTenantId() {
        assertThatThrownBy(() -> FlagContext.forTenant(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void current_returnsAnonymousContext() {
        assertThat(FlagContext.current()).isEqualTo(FlagContext.anonymous());
    }
}
