package com.acme.flags.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class InteractionReportTest {

    private static final Interaction FINDING = new Interaction(
            Interaction.Category.NESTING, List.of("svc.x", "svc.y"), "F.java:12",
            "'svc.y' evaluated inside a branch guarded by 'svc.x'");

    @Test
    void textReportHasAdvisoryHeaderAndTheFinding() {
        String text = InteractionReport.toText(List.of(FINDING));

        assertThat(text).contains("ADVISORY ONLY");
        assertThat(text).contains("~90%");
        assertThat(text).contains("NESTING");
        assertThat(text).contains("F.java:12");
    }

    @Test
    void jsonReportIsAdvisoryAndEscapesStrings() {
        String json = InteractionReport.toJson(List.of(FINDING));

        assertThat(json).startsWith("{\"advisory\":true,\"count\":1");
        assertThat(json).contains("\"category\":\"NESTING\"");
        assertThat(json).contains("\"svc.y\"");
    }

    @Test
    void escEscapesQuotesBackslashesAndControls() {
        assertThat(InteractionReport.esc("a\"b\\c\n")).isEqualTo("a\\\"b\\\\c\\n");
    }
}
