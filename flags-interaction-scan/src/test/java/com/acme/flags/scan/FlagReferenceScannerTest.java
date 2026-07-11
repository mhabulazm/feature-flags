package com.acme.flags.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FlagReferenceScannerTest {

    private List<FlagReference> scan(String dir, String... files) {
        List<Path> paths = java.util.Arrays.stream(files).map(f -> Path.of(dir, f)).toList();
        return new FlagReferenceScanner(FlagRegistryIndex.buildFrom(paths)).scan(paths);
    }

    @Test
    void twoFlagsInOneMethodShareAMethodId() {
        List<FlagReference> refs = scan("src/test/resources/fixtures/coreference",
                "CheckoutFlags.java", "CheckoutService.java");

        assertThat(refs).hasSize(2);
        assertThat(refs.get(0).methodId()).isEqualTo(refs.get(1).methodId());
        assertThat(refs.get(0).methodId()).contains("#render#");
        assertThat(refs).allSatisfy(r -> assertThat(r.owningNamespace()).isEqualTo("checkout"));
    }

    @Test
    void nestedFlagCarriesTheOuterFlagAsAGuardKey() {
        List<FlagReference> refs = scan("src/test/resources/fixtures/nesting",
                "SearchFlags.java", "SearchService.java");

        FlagReference inner = refs.stream()
                .filter(r -> r.flagKey().constant().equals("TYPO_TOLERANCE"))
                .findFirst().orElseThrow();
        assertThat(inner.guardKeys()).contains("search.new-ranking");

        FlagReference outer = refs.stream()
                .filter(r -> r.flagKey().constant().equals("NEW_RANKING"))
                .findFirst().orElseThrow();
        assertThat(outer.guardKeys()).doesNotContain("search.new-ranking");
    }
}
