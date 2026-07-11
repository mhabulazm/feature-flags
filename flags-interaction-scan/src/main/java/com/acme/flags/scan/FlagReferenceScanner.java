package com.acme.flags.scan;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.IfStmt;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Finds flag-evaluation call sites and enriches each with method + if-guard context. */
public final class FlagReferenceScanner {

    private static final Set<String> EVAL_METHODS = Set.of("isEnabled", "getVariant", "getConfigValue");

    private final FlagRegistryIndex index;

    public FlagReferenceScanner(FlagRegistryIndex index) {
        this.index = index;
    }

    public List<FlagReference> scan(List<Path> javaFiles) {
        List<FlagReference> refs = new ArrayList<>();
        for (Path file : javaFiles) {
            CompilationUnit cu = FlagRegistryIndex.parse(file);
            String owning = owningNamespaceOf(cu);
            String fileName = file.toString();
            for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
                if (!EVAL_METHODS.contains(call.getNameAsString())) {
                    continue;
                }
                for (Expression arg : call.getArguments()) {
                    Optional<FlagKeyRef> match = matchFlagKey(arg);
                    if (match.isEmpty()) {
                        continue;
                    }
                    refs.add(new FlagReference(
                            match.get(),
                            fileName,
                            arg.getBegin().map(p -> p.line).orElse(-1),
                            methodId(fileName, arg),
                            owning,
                            guardKeys(arg)));
                }
            }
        }
        return refs;
    }

    private Optional<FlagKeyRef> matchFlagKey(Expression expr) {
        if (expr instanceof FieldAccessExpr fa) {
            return index.lookup(simpleName(fa.getScope().toString()), fa.getNameAsString());
        }
        return Optional.empty();
    }

    private Set<String> guardKeys(Expression argExpr) {
        Set<String> keys = new LinkedHashSet<>();
        Node n = argExpr;
        while (n.getParentNode().isPresent()) {
            Node parent = n.getParentNode().get();
            if (parent instanceof IfStmt ifs) {
                boolean inBranch = ifs.getThenStmt() == n
                        || (ifs.getElseStmt().isPresent() && ifs.getElseStmt().get() == n);
                if (inBranch) {
                    for (FieldAccessExpr fa : ifs.getCondition().findAll(FieldAccessExpr.class)) {
                        matchFlagKey(fa).ifPresent(ref -> keys.add(ref.key()));
                    }
                }
            }
            n = parent;
        }
        return keys;
    }

    private String owningNamespaceOf(CompilationUnit cu) {
        for (EnumDeclaration en : cu.findAll(EnumDeclaration.class)) {
            if (!FlagRegistryIndex.implementsFlagKey(en)) {
                continue;
            }
            for (EnumConstantDeclaration entry : en.getEntries()) {
                String ns = FlagRegistryIndex.namespaceOf(FlagRegistryIndex.firstStringArg(entry).orElse(""));
                if (!ns.isBlank()) {
                    return ns;
                }
            }
        }
        return cu.getPackageDeclaration().map(pd -> simpleName(pd.getNameAsString())).orElse("");
    }

    private static String methodId(String file, Node node) {
        Optional<MethodDeclaration> m = node.findAncestor(MethodDeclaration.class);
        String name = m.map(MethodDeclaration::getNameAsString).orElse("<init>");
        int line = m.flatMap(d -> d.getBegin().map(p -> p.line)).orElse(-1);
        return file + "#" + name + "#L" + line;
    }

    private static String simpleName(String qualified) {
        int dot = qualified.lastIndexOf('.');
        return dot >= 0 ? qualified.substring(dot + 1) : qualified;
    }
}
