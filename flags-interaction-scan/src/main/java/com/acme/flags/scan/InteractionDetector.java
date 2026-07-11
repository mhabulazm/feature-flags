package com.acme.flags.scan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Applies ADR 0003's three interaction categories over scanned references. Read-only: returns findings, mutates nothing. */
public final class InteractionDetector {

    public List<Interaction> detect(List<FlagReference> refs) {
        List<Interaction> out = new ArrayList<>();
        out.addAll(coReference(refs));
        out.addAll(nesting(refs));
        out.addAll(crossService(refs));
        return out;
    }

    private List<Interaction> coReference(List<FlagReference> refs) {
        Map<String, List<FlagReference>> byMethod =
                refs.stream().collect(Collectors.groupingBy(FlagReference::methodId));
        List<Interaction> out = new ArrayList<>();
        for (Map.Entry<String, List<FlagReference>> e : new TreeMap<>(byMethod).entrySet()) {
            Set<String> keys = e.getValue().stream()
                    .map(r -> r.flagKey().key())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (keys.size() >= 2) {
                FlagReference first = e.getValue().get(0);
                out.add(new Interaction(Interaction.Category.CO_REFERENCE, List.copyOf(keys),
                        first.file() + ":" + first.line(),
                        keys.size() + " flags evaluated in the same method (" + e.getKey() + ")"));
            }
        }
        return out;
    }

    private List<Interaction> nesting(List<FlagReference> refs) {
        List<Interaction> out = new ArrayList<>();
        for (FlagReference r : refs) {
            for (String guard : r.guardKeys()) {
                if (!guard.equals(r.flagKey().key())) {
                    out.add(new Interaction(Interaction.Category.NESTING, List.of(guard, r.flagKey().key()),
                            r.file() + ":" + r.line(),
                            "'" + r.flagKey().key() + "' evaluated inside a branch guarded by '" + guard + "'"));
                }
            }
        }
        return out;
    }

    private List<Interaction> crossService(List<FlagReference> refs) {
        List<Interaction> out = new ArrayList<>();
        for (FlagReference r : refs) {
            String keyNs = r.flagKey().namespace();
            String owning = r.owningNamespace();
            if (!keyNs.isBlank() && !owning.isBlank() && !keyNs.equals(owning)) {
                out.add(new Interaction(Interaction.Category.CROSS_SERVICE, List.of(r.flagKey().key()),
                        r.file() + ":" + r.line(),
                        "service '" + owning + "' references '" + r.flagKey().key() + "' owned by '" + keyNs + "'"));
            }
        }
        return out;
    }
}
