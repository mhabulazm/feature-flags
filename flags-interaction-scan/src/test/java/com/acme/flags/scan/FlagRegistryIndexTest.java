package com.acme.flags.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlagRegistryIndexTest {

    @Test
    void indexesEnumConstantsWithNamespace() {
        List<Path> files = List.of(Path.of("src/test/resources/fixtures/coreference/CheckoutFlags.java"));

        FlagRegistryIndex index = FlagRegistryIndex.buildFrom(files);

        assertThat(index.size()).isEqualTo(2);
        assertThat(index.lookup("CheckoutFlags", "NEW_CHECKOUT")).hasValueSatisfying(ref -> {
            assertThat(ref.key()).isEqualTo("checkout.new-checkout");
            assertThat(ref.namespace()).isEqualTo("checkout");
        });
        assertThat(index.lookup("CheckoutFlags", "MISSING")).isEmpty();
    }
}
