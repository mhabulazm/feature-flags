package com.acme.flags.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InteractionDetectorTest {

    private static FlagReference ref(String enumName, String constant, String key, String ns,
                                     String methodId, String owningNs, Set<String> guardKeys) {
        return new FlagReference(new FlagKeyRef(enumName, constant, key, ns), "F.java", 1, methodId, owningNs, guardKeys);
    }

    @Test
    void detectsCoReferenceWhenTwoKeysShareAMethod() {
        List<FlagReference> refs = List.of(
                ref("A", "X", "svc.x", "svc", "F#m#L1", "svc", Set.of()),
                ref("A", "Y", "svc.y", "svc", "F#m#L1", "svc", Set.of()));

        List<Interaction> out = new InteractionDetector().detect(refs);

        assertThat(out).filteredOn(i -> i.category() == Interaction.Category.CO_REFERENCE)
                .singleElement()
                .satisfies(i -> assertThat(i.keys()).containsExactlyInAnyOrder("svc.x", "svc.y"));
    }

    @Test
    void detectsNestingFromGuardKeys() {
        List<FlagReference> refs = List.of(
                ref("A", "Y", "svc.y", "svc", "F#m#L1", "svc", Set.of("svc.x")));

        List<Interaction> out = new InteractionDetector().detect(refs);

        assertThat(out).filteredOn(i -> i.category() == Interaction.Category.NESTING)
                .singleElement()
                .satisfies(i -> assertThat(i.keys()).containsExactly("svc.x", "svc.y"));
    }

    @Test
    void detectsCrossServiceWhenNamespaceDiffersFromOwner() {
        List<FlagReference> refs = List.of(
                ref("B", "R", "billing.rate", "billing", "F#m#L1", "checkout", Set.of()));

        List<Interaction> out = new InteractionDetector().detect(refs);

        assertThat(out).filteredOn(i -> i.category() == Interaction.Category.CROSS_SERVICE)
                .singleElement()
                .satisfies(i -> assertThat(i.keys()).containsExactly("billing.rate"));
    }

    @Test
    void cleanReferencesProduceNoFindings() {
        List<FlagReference> refs = List.of(
                ref("A", "X", "svc.x", "svc", "F#m1#L1", "svc", Set.of()),
                ref("A", "Y", "svc.y", "svc", "F#m2#L9", "svc", Set.of()));

        assertThat(new InteractionDetector().detect(refs)).isEmpty();
    }
}
