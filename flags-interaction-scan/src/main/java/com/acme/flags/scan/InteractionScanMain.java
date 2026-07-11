package com.acme.flags.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** CLI entrypoint for the advisory interaction scan. Always exits 0 -- the scan is advisory, never a gate. */
public final class InteractionScanMain {

    private InteractionScanMain() {
    }

    public static void main(String[] args) {
        boolean json = false;
        List<Path> roots = new ArrayList<>();
        for (String a : args) {
            if (a.equals("--json")) {
                json = true;
            } else {
                roots.add(Path.of(a));
            }
        }
        System.out.println(run(roots, json));
        // Deliberately no System.exit(non-zero): advisory only, must never fail a build.
    }

    public static String run(List<Path> roots, boolean json) {
        List<Path> javaFiles = collectJavaFiles(roots);
        FlagRegistryIndex index = FlagRegistryIndex.buildFrom(javaFiles);
        List<FlagReference> refs = new FlagReferenceScanner(index).scan(javaFiles);
        List<Interaction> interactions = new InteractionDetector().detect(refs);
        return json ? InteractionReport.toJson(interactions) : InteractionReport.toText(interactions);
    }

    static List<Path> collectJavaFiles(List<Path> roots) {
        List<Path> files = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.toString().endsWith(".java")).forEach(files::add);
            } catch (IOException e) {
                throw new RuntimeException("Failed to walk " + root, e);
            }
        }
        return files;
    }
}
