package com.acme.flags.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class InteractionScanMainTest {

    @Test
    void endToEndOverAllFixturesFindsEveryCategory() {
        String report = InteractionScanMain.run(List.of(Path.of("src/test/resources/fixtures")), false);

        assertThat(report).contains("== CO_REFERENCE (");
        assertThat(report).contains("== NESTING (");
        assertThat(report).contains("== CROSS_SERVICE (");
        assertThat(report).contains("billing.rate-limit-override");
        assertThat(report).contains("search.typo-tolerance");
    }

    @Test
    void negativeFixtureProducesNoFindings() {
        String report = InteractionScanMain.run(List.of(Path.of("src/test/resources/fixtures/negative")), false);

        assertThat(report).contains("0 interaction finding(s).");
        assertThat(report).contains("== CO_REFERENCE (0) ==");
        assertThat(report).contains("== NESTING (0) ==");
        assertThat(report).contains("== CROSS_SERVICE (0) ==");
    }

    @Test
    void jsonModeEmitsAdvisoryEnvelope() {
        String json = InteractionScanMain.run(List.of(Path.of("src/test/resources/fixtures/crossservice")), true);

        assertThat(json).startsWith("{\"advisory\":true");
        assertThat(json).contains("\"category\":\"CROSS_SERVICE\"");
    }
}
