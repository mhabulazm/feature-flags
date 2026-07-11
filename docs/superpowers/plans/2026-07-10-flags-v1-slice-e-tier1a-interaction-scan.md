# Slice E Tier 1a — Flag Interaction Scan Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone build-time static scanner that surfaces cross-flag interactions (co-reference, nesting, cross-service) across `FlagKey` registries as a read-only, advisory report.

**Architecture:** A new Maven module `flags-interaction-scan` with a four-stage pipeline — `FlagRegistryIndex` (inventory the `FlagKey` enum constants) → `FlagReferenceScanner` (find evaluation call sites, enriched with method + if-guard context) → `InteractionDetector` (apply the three category rules) → `InteractionReport` + `InteractionScanMain` (render + CLI). Detection is purely syntactic via JavaParser: no symbol resolution, no compilation.

**Tech Stack:** Java 21, JavaParser (`com.github.javaparser:javaparser-core:3.28.2`), JUnit 5 + AssertJ (versions from the parent `spring-boot-dependencies` import).

## Global Constraints

- **Java 21** (`maven.compiler.release` inherited from `flags-parent`).
- **Excluded from `flags-bom`.** This is CI tooling, not a published `flags-*` artifact — never add it to `flags-bom/pom.xml` (mirrors `flags-benchmark-goff`).
- **Read-only / advisory, enforced structurally.** No code path mutates, toggles, or persists anything. The CLI exits 0 regardless of findings — it must never fail a build. (ADR 0003 / ADR 0002 guardrail.)
- **Syntactic detection only** in Tier 1a — no `javaparser-symbol-solver-core`, no classpath resolution. False positives are expected (~90% on raw findings, per source [24]); the advisory-only contract is the mitigation.
- **Package:** `com.acme.flags.scan`.
- **Commit style:** plain imperative subject lines (e.g. `Add FlagRegistryIndex...`), matching repo history. **No `Co-Authored-By: Claude` trailer.**
- **Out of scope:** cross-repo aggregation (Tier 1b), Tier 1.5 similarity triage, any blocking / graduation / Tier-2 thresholds.

## File Structure

```
flags-interaction-scan/
  pom.xml                                             (Task 1)
  src/main/java/com/acme/flags/scan/
    package-info.java                                 (Task 1)
    FlagKeyRef.java            record                 (Task 1)
    FlagRegistryIndex.java     parse enums -> index   (Task 1)
    FlagReference.java         record                 (Task 2)
    FlagReferenceScanner.java  find + enrich refs     (Task 2)
    Interaction.java           record + Category enum (Task 3)
    InteractionDetector.java   the three rules        (Task 3)
    InteractionReport.java     text + JSON render     (Task 4)
    InteractionScanMain.java   CLI + pipeline wiring  (Task 5)
  src/test/java/com/acme/flags/scan/
    FlagRegistryIndexTest.java                        (Task 1)
    FlagReferenceScannerTest.java                     (Task 2)
    InteractionDetectorTest.java                      (Task 3)
    InteractionReportTest.java                        (Task 4)
    InteractionScanMainTest.java                      (Task 5)
  src/test/resources/fixtures/
    coreference/CheckoutFlags.java, CheckoutService.java     (Task 2)
    nesting/SearchFlags.java, SearchService.java             (Task 2)
    crossservice/billing/BillingFlags.java                   (Task 5)
    crossservice/checkout/CheckoutBillingUsage.java          (Task 5)
    negative/OrdersFlags.java, OrdersService.java            (Task 5)
```
Root `pom.xml` `<modules>` gains `flags-interaction-scan` (Task 1).

---

### Task 1: Scaffold module + `FlagRegistryIndex`

**Files:**
- Create: `flags-interaction-scan/pom.xml`
- Modify: `pom.xml` (root — add module to `<modules>`)
- Create: `flags-interaction-scan/src/main/java/com/acme/flags/scan/package-info.java`
- Create: `flags-interaction-scan/src/main/java/com/acme/flags/scan/FlagKeyRef.java`
- Create: `flags-interaction-scan/src/main/java/com/acme/flags/scan/FlagRegistryIndex.java`
- Test: `flags-interaction-scan/src/test/java/com/acme/flags/scan/FlagRegistryIndexTest.java`
- Fixture: `flags-interaction-scan/src/test/resources/fixtures/coreference/CheckoutFlags.java`

**Interfaces:**
- Produces: `FlagKeyRef(String enumName, String constant, String key, String namespace)`; `FlagRegistryIndex.buildFrom(List<Path>) -> FlagRegistryIndex`; `index.lookup(String enumSimpleName, String constant) -> Optional<FlagKeyRef>`; `index.size() -> int`; static helpers `FlagRegistryIndex.implementsFlagKey(EnumDeclaration)`, `FlagRegistryIndex.firstStringArg(EnumConstantDeclaration) -> Optional<String>`, `FlagRegistryIndex.namespaceOf(String) -> String`, `FlagRegistryIndex.parse(Path) -> CompilationUnit`.

- [ ] **Step 1: Create the module pom**

`flags-interaction-scan/pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.acme.flags</groupId>
        <artifactId>flags-parent</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>flags-interaction-scan</artifactId>
    <packaging>jar</packaging>
    <name>Flags Interaction Scan (v1 Slice E, ADR 0003 Tier 1a)</name>
    <description>Build-time static scan surfacing cross-flag interactions (co-reference, nesting, cross-service) as a read-only advisory report. NOT a published flags-* artifact -- excluded from flags-bom. Tier 1a (single-service detection); cross-repo aggregation (Tier 1b) is out of scope.</description>

    <properties>
        <javaparser.version>3.28.2</javaparser.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>com.github.javaparser</groupId>
            <artifactId>javaparser-core</artifactId>
            <version>${javaparser.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
                <version>3.5.0</version>
                <configuration>
                    <mainClass>com.acme.flags.scan.InteractionScanMain</mainClass>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Register the module in the root reactor (NOT in the BOM)**

In root `pom.xml`, add the module after `flags-benchmark-goff`:
```xml
        <module>flags-benchmark-goff</module>
        <module>flags-interaction-scan</module>
        <module>flags-test-fixtures</module>
```
Do **not** touch `flags-bom/pom.xml`.

- [ ] **Step 3: Create `package-info.java` and the `FlagKeyRef` record**

`.../scan/package-info.java`:
```java
/**
 * ADR 0003 Tier 1a flag-interaction scanner: a read-only, advisory static scan over {@code FlagKey}
 * registries. Syntactic (JavaParser) detection only — no symbol resolution, no compilation.
 */
package com.acme.flags.scan;
```

`.../scan/FlagKeyRef.java`:
```java
package com.acme.flags.scan;

/**
 * One flag key discovered in a registry enum: the declaring enum + constant, its key string, and its
 * namespace (the prefix before the first '.', e.g. "billing" from "billing.rate-limit-override").
 */
public record FlagKeyRef(String enumName, String constant, String key, String namespace) {
}
```

- [ ] **Step 4: Create the co-reference fixture (used by this task's index test and Task 2)**

`.../src/test/resources/fixtures/coreference/CheckoutFlags.java`:
```java
package fixtures.checkout;

public enum CheckoutFlags implements FlagKey {
    NEW_CHECKOUT("checkout.new-checkout"),
    EXPRESS_PAY("checkout.express-pay");

    private final String key;

    CheckoutFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
```

- [ ] **Step 5: Write the failing test**

`.../src/test/java/com/acme/flags/scan/FlagRegistryIndexTest.java`:
```java
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
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=FlagRegistryIndexTest`
Expected: FAIL — compilation error, `FlagRegistryIndex` does not exist.

- [ ] **Step 7: Implement `FlagRegistryIndex`**

`.../scan/FlagRegistryIndex.java`:
```java
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
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=FlagRegistryIndexTest`
Expected: PASS (`Tests run: 1, Failures: 0`).

- [ ] **Step 9: Commit**

```bash
git add flags-interaction-scan/pom.xml pom.xml flags-interaction-scan/src
git commit -m "Add flags-interaction-scan module + FlagRegistryIndex (Slice E Tier 1a)"
```

---

### Task 2: `FlagReferenceScanner`

**Files:**
- Create: `.../scan/FlagReference.java`
- Create: `.../scan/FlagReferenceScanner.java`
- Test: `.../scan/FlagReferenceScannerTest.java`
- Fixtures: `.../fixtures/coreference/CheckoutService.java`, `.../fixtures/nesting/SearchFlags.java`, `.../fixtures/nesting/SearchService.java`

**Interfaces:**
- Consumes: `FlagRegistryIndex`, `FlagKeyRef` (Task 1).
- Produces: `FlagReference(FlagKeyRef flagKey, String file, int line, String methodId, String owningNamespace, Set<String> guardKeys)`; `new FlagReferenceScanner(FlagRegistryIndex).scan(List<Path>) -> List<FlagReference>`.

- [ ] **Step 1: Create the fixtures**

`.../fixtures/coreference/CheckoutService.java`:
```java
package fixtures.checkout;

public class CheckoutService {
    private FeatureFlags flags;

    void render() {
        boolean a = flags.isEnabled(CheckoutFlags.NEW_CHECKOUT);
        boolean b = flags.isEnabled(CheckoutFlags.EXPRESS_PAY);
    }
}
```

`.../fixtures/nesting/SearchFlags.java`:
```java
package fixtures.search;

public enum SearchFlags implements FlagKey {
    NEW_RANKING("search.new-ranking"),
    TYPO_TOLERANCE("search.typo-tolerance");

    private final String key;

    SearchFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
```

`.../fixtures/nesting/SearchService.java`:
```java
package fixtures.search;

public class SearchService {
    private FeatureFlags flags;

    void search() {
        if (flags.isEnabled(SearchFlags.NEW_RANKING)) {
            if (flags.isEnabled(SearchFlags.TYPO_TOLERANCE)) {
                doSearch();
            }
        }
    }

    void doSearch() {
    }
}
```

- [ ] **Step 2: Write the failing test**

`.../scan/FlagReferenceScannerTest.java`:
```java
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=FlagReferenceScannerTest`
Expected: FAIL — `FlagReference` / `FlagReferenceScanner` do not exist.

- [ ] **Step 4: Create the `FlagReference` record**

`.../scan/FlagReference.java`:
```java
package com.acme.flags.scan;

import java.util.Set;

/** A flag-key use at a call site, with the method + if-guard context the detector needs. */
public record FlagReference(
        FlagKeyRef flagKey,
        String file,
        int line,
        String methodId,
        String owningNamespace,
        Set<String> guardKeys) {
}
```

- [ ] **Step 5: Implement `FlagReferenceScanner`**

`.../scan/FlagReferenceScanner.java`:
```java
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
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=FlagReferenceScannerTest`
Expected: PASS (`Tests run: 2, Failures: 0`).

- [ ] **Step 7: Commit**

```bash
git add flags-interaction-scan/src
git commit -m "Add FlagReferenceScanner: find + enrich flag references (Slice E Tier 1a)"
```

---

### Task 3: `InteractionDetector` + the three rules

**Files:**
- Create: `.../scan/Interaction.java`
- Create: `.../scan/InteractionDetector.java`
- Test: `.../scan/InteractionDetectorTest.java`

**Interfaces:**
- Consumes: `FlagReference`, `FlagKeyRef` (Tasks 1-2).
- Produces: `Interaction(Interaction.Category category, List<String> keys, String site, String detail)`; enum `Interaction.Category { CO_REFERENCE, NESTING, CROSS_SERVICE }`; `new InteractionDetector().detect(List<FlagReference>) -> List<Interaction>`.

- [ ] **Step 1: Write the failing test**

`.../scan/InteractionDetectorTest.java`:
```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=InteractionDetectorTest`
Expected: FAIL — `Interaction` / `InteractionDetector` do not exist.

- [ ] **Step 3: Create the `Interaction` record**

`.../scan/Interaction.java`:
```java
package com.acme.flags.scan;

import java.util.List;

/** One interaction finding. Read-only data; carries category, the involved keys, its site, and a human detail. */
public record Interaction(Category category, List<String> keys, String site, String detail) {

    public enum Category {
        CO_REFERENCE, NESTING, CROSS_SERVICE
    }
}
```

- [ ] **Step 4: Implement `InteractionDetector`**

`.../scan/InteractionDetector.java`:
```java
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=InteractionDetectorTest`
Expected: PASS (`Tests run: 4, Failures: 0`).

- [ ] **Step 6: Commit**

```bash
git add flags-interaction-scan/src
git commit -m "Add InteractionDetector: co-reference/nesting/cross-service rules (Slice E Tier 1a)"
```

---

### Task 4: `InteractionReport` (text + JSON, advisory)

**Files:**
- Create: `.../scan/InteractionReport.java`
- Test: `.../scan/InteractionReportTest.java`

**Interfaces:**
- Consumes: `Interaction` (Task 3).
- Produces: static `InteractionReport.toText(List<Interaction>) -> String`; static `InteractionReport.toJson(List<Interaction>) -> String`; static `InteractionReport.esc(String) -> String`.

- [ ] **Step 1: Write the failing test**

`.../scan/InteractionReportTest.java`:
```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=InteractionReportTest`
Expected: FAIL — `InteractionReport` does not exist.

- [ ] **Step 3: Implement `InteractionReport`**

`.../scan/InteractionReport.java`:
```java
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
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=InteractionReportTest`
Expected: PASS (`Tests run: 3, Failures: 0`).

- [ ] **Step 5: Commit**

```bash
git add flags-interaction-scan/src
git commit -m "Add InteractionReport: text + JSON advisory rendering (Slice E Tier 1a)"
```

---

### Task 5: `InteractionScanMain` CLI + end-to-end fixtures

**Files:**
- Create: `.../scan/InteractionScanMain.java`
- Test: `.../scan/InteractionScanMainTest.java`
- Fixtures: `.../fixtures/crossservice/billing/BillingFlags.java`, `.../fixtures/crossservice/checkout/CheckoutBillingUsage.java`, `.../fixtures/negative/OrdersFlags.java`, `.../fixtures/negative/OrdersService.java`

**Interfaces:**
- Consumes: all prior units.
- Produces: `InteractionScanMain.run(List<Path> roots, boolean json) -> String`; `InteractionScanMain.main(String[])`; static `InteractionScanMain.collectJavaFiles(List<Path>) -> List<Path>`.

- [ ] **Step 1: Create the cross-service and negative fixtures**

`.../fixtures/crossservice/billing/BillingFlags.java`:
```java
package fixtures.billing;

public enum BillingFlags implements FlagKey {
    RATE_LIMIT("billing.rate-limit-override");

    private final String key;

    BillingFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
```

`.../fixtures/crossservice/checkout/CheckoutBillingUsage.java`:
```java
package fixtures.checkout;

public class CheckoutBillingUsage {
    private FeatureFlags flags;

    void apply() {
        boolean limited = flags.isEnabled(BillingFlags.RATE_LIMIT);
    }
}
```

`.../fixtures/negative/OrdersFlags.java`:
```java
package fixtures.orders;

public enum OrdersFlags implements FlagKey {
    SPLIT_SHIPMENT("orders.split-shipment"),
    GIFT_WRAP("orders.gift-wrap");

    private final String key;

    OrdersFlags(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
```

`.../fixtures/negative/OrdersService.java`:
```java
package fixtures.orders;

public class OrdersService {
    private FeatureFlags flags;

    void ship() {
        if (flags.isEnabled(OrdersFlags.SPLIT_SHIPMENT)) {
            doShip();
        }
    }

    void wrap() {
        boolean w = flags.isEnabled(OrdersFlags.GIFT_WRAP);
    }

    void doShip() {
    }
}
```

- [ ] **Step 2: Write the failing test**

`.../scan/InteractionScanMainTest.java`:
```java
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
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=InteractionScanMainTest`
Expected: FAIL — `InteractionScanMain` does not exist.

- [ ] **Step 4: Implement `InteractionScanMain`**

`.../scan/InteractionScanMain.java`:
```java
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
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `mvn -q -pl flags-interaction-scan test -Dtest=InteractionScanMainTest`
Expected: PASS (`Tests run: 3, Failures: 0`).

- [ ] **Step 6: Run the whole module + a live scan to verify end-to-end**

Run: `mvn -q -pl flags-interaction-scan test`
Expected: PASS (`Tests run: 13, Failures: 0, Errors: 0`).

Run: `mvn -q -pl flags-interaction-scan compile exec:java -Dexec.arguments=flags-interaction-scan/src/test/resources/fixtures`
Expected: prints the advisory report to stdout with non-zero CO_REFERENCE / NESTING / CROSS_SERVICE counts; process exits 0.

- [ ] **Step 7: Commit**

```bash
git add flags-interaction-scan/src
git commit -m "Add InteractionScanMain CLI + end-to-end fixtures (Slice E Tier 1a)"
```

---

### Task 6: Reactor verify + module README

**Files:**
- Create: `flags-interaction-scan/README.md`

- [ ] **Step 1: Full reactor build to confirm nothing else broke**

Run: `mvn -q -T1C verify`
Expected: `BUILD SUCCESS` across all 9 modules (8 prior + `flags-interaction-scan`).

- [ ] **Step 2: Confirm the module is NOT in the BOM**

Run: `grep -c flags-interaction-scan flags-bom/pom.xml`
Expected: `0`.

- [ ] **Step 3: Write the module README**

`flags-interaction-scan/README.md`:
```markdown
# flags-interaction-scan (Slice E, ADR 0003 Tier 1a)

Build-time static scan that surfaces cross-flag interactions across `FlagKey` registries as a
**read-only, advisory** report. Not a published `flags-*` artifact — excluded from `flags-bom`;
nothing in the facade depends on it.

## What it detects

- **Co-reference** — two or more distinct flag keys evaluated in the same method.
- **Nesting** — a flag evaluated inside a branch guarded by a different flag.
- **Cross-service** — a reference to a flag whose namespace differs from the referencing file's.

Detection is purely syntactic (JavaParser), with no symbol resolution — so expect a high
false-positive rate (~90% on raw findings for scans of this kind). Every finding is a review
prompt, not a defect; the scan never blocks a build and exits 0 regardless of findings.

## Running it

```bash
mvn -pl flags-interaction-scan compile exec:java -Dexec.arguments=<source-root>[,<root2>] [--json]
```

## Scope

Tier 1a — single-service detection, proven against the synthetic fixtures under
`src/test/resources/fixtures/`. Cross-repo aggregation (Tier 1b) is out of scope until real
consuming services with their own registries exist. See
`../docs/superpowers/specs/2026-07-09-flags-v1-slice-e-interaction-scan-design.md`.
```

- [ ] **Step 4: Commit**

```bash
git add flags-interaction-scan/README.md
git commit -m "Add flags-interaction-scan README; reactor verify green (Slice E Tier 1a)"
```

---

## Self-Review

**Spec coverage:**
- Module excluded from BOM → Task 1 (root pom only) + Task 6 Step 2 assertion. ✓
- `FlagRegistryIndex` inventory of keys + namespace → Task 1. ✓
- Reference scanning of `isEnabled`/`getVariant`/`getConfigValue` → Task 2 (`EVAL_METHODS`). ✓
- Co-reference / nesting / cross-service rules → Task 3. ✓
- Read-only advisory report, text + `--json`, ~90% FP header, exit 0 → Task 4 + Task 5. ✓
- Syntactic-only (no symbol-solver) → no such dependency in Task 1 pom; noted in README. ✓
- Synthetic fixtures incl. a negative case → Tasks 2 + 5. ✓
- Out-of-scope (Tier 1b, Tier 1.5, thresholds) → not built; stated in README + spec. ✓

**Placeholder scan:** No TBD/TODO; every code step contains complete code. ✓

**Type consistency:** `FlagKeyRef`, `FlagReference`, `Interaction`/`Interaction.Category`, and the method signatures (`buildFrom`, `lookup`, `scan`, `detect`, `toText`/`toJson`/`esc`, `run`/`collectJavaFiles`) are used identically across tasks. `FlagRegistryIndex.parse`/`implementsFlagKey`/`firstStringArg`/`namespaceOf` are defined in Task 1 and reused in Task 2. ✓
