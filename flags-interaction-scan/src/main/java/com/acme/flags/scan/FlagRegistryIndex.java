package com.acme.flags.scan;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Inventory of every {@code FlagKey} enum constant under the scanned sources, keyed by "EnumName.CONSTANT". */
public final class FlagRegistryIndex {

    private final Map<String, FlagKeyRef> byQualifiedConstant;

    private FlagRegistryIndex(Map<String, FlagKeyRef> byQualifiedConstant) {
        this.byQualifiedConstant = byQualifiedConstant;
    }

    public static FlagRegistryIndex buildFrom(List<Path> javaFiles) {
        Map<String, FlagKeyRef> index = new HashMap<>();
        for (Path file : javaFiles) {
            CompilationUnit cu = parse(file);
            for (EnumDeclaration en : cu.findAll(EnumDeclaration.class)) {
                if (!implementsFlagKey(en)) {
                    continue;
                }
                String enumName = en.getNameAsString();
                for (EnumConstantDeclaration entry : en.getEntries()) {
                    String key = firstStringArg(entry).orElse("");
                    index.put(enumName + "." + entry.getNameAsString(),
                            new FlagKeyRef(enumName, entry.getNameAsString(), key, namespaceOf(key)));
                }
            }
        }
        return new FlagRegistryIndex(index);
    }

    public Optional<FlagKeyRef> lookup(String enumSimpleName, String constant) {
        return Optional.ofNullable(byQualifiedConstant.get(enumSimpleName + "." + constant));
    }

    public int size() {
        return byQualifiedConstant.size();
    }

    static boolean implementsFlagKey(EnumDeclaration en) {
        return en.getImplementedTypes().stream().anyMatch(t -> t.getNameAsString().equals("FlagKey"));
    }

    static Optional<String> firstStringArg(EnumConstantDeclaration entry) {
        if (!entry.getArguments().isEmpty() && entry.getArguments().get(0) instanceof StringLiteralExpr s) {
            return Optional.of(s.asString());
        }
        return Optional.empty();
    }

    static String namespaceOf(String key) {
        int dot = key.indexOf('.');
        return dot > 0 ? key.substring(0, dot) : "";
    }

    static CompilationUnit parse(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return StaticJavaParser.parse(in);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse " + file, e);
        }
    }
}
