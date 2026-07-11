package com.acme.flags.scan;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Renders interaction findings as a read-only, advisory report (plain text or JSON). */
public final class InteractionReport {

    private static final String ADVISORY_HEADER =
            "Flag interaction scan (ADR 0003 Tier 1) -- ADVISORY ONLY, does not block.\n"
          + "Expect a high false-positive rate (~90% on raw findings for scans of this kind);\n"
          + "each finding is a review prompt, not a defect.\n";

    private InteractionReport() {
    }

    public static String toText(List<Interaction> interactions) {
        StringBuilder sb = new StringBuilder(ADVISORY_HEADER);
        sb.append("\n").append(interactions.size()).append(" interaction finding(s).\n");
        Map<Interaction.Category, List<Interaction>> byCat =
                interactions.stream().collect(Collectors.groupingBy(Interaction::category));
        for (Interaction.Category cat : Interaction.Category.values()) {
            List<Interaction> items = byCat.getOrDefault(cat, List.of());
            sb.append("\n== ").append(cat).append(" (").append(items.size()).append(") ==\n");
            for (Interaction i : items) {
                sb.append("  ").append(i.site()).append("  ").append(i.detail())
                        .append("  ").append(i.keys()).append("\n");
            }
        }
        return sb.toString();
    }

    public static String toJson(List<Interaction> interactions) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"advisory\":true,\"count\":").append(interactions.size()).append(",\"findings\":[");
        for (int idx = 0; idx < interactions.size(); idx++) {
            Interaction i = interactions.get(idx);
            if (idx > 0) {
                sb.append(",");
            }
            sb.append("{\"category\":\"").append(i.category()).append("\",")
                    .append("\"site\":\"").append(esc(i.site())).append("\",")
                    .append("\"detail\":\"").append(esc(i.detail())).append("\",")
                    .append("\"keys\":[");
            for (int k = 0; k < i.keys().size(); k++) {
                if (k > 0) {
                    sb.append(",");
                }
                sb.append("\"").append(esc(i.keys().get(k))).append("\"");
            }
            sb.append("]}");
        }
        sb.append("]}");
        return sb.toString();
    }

    static String esc(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }
}
