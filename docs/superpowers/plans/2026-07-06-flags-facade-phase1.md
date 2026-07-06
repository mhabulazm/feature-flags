# Flags Facade Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the 6 vendor-free Maven modules of the in-house feature-flag facade (`flags-api`, `flags-contract-test`, `flags-noop`, `flags-test-fixtures`, `flags-spring-boot-starter`, `flags-bom`) as real, tested, CI-verified Java code, per `docs/superpowers/specs/2026-07-06-flags-facade-phase1-design.md`.

**Architecture:** A Maven multi-module reactor rooted at the repo root. `flags-api` holds the public `FeatureFlags`/`FlagKey`/`FlagContext`/`FlagMetadata`/`ConfigKey` contract, the `FlagEngine` SPI, and the `DefaultFeatureFlags` reference implementation — zero vendor/engine dependency, enforced by an ArchUnit test. `flags-noop` ships `NoOpFlagEngine` and `InMemoryFlagEngine`. `flags-contract-test` ships an abstract JUnit base class every `FlagEngine` implementation must pass. `flags-test-fixtures` ships `FakeFeatureFlags` for consumers. `flags-spring-boot-starter` auto-wires a `FeatureFlags` bean from whichever engine is configured. `flags-bom` pins versions for consumers.

**Tech Stack:** Java 21, Maven multi-module reactor, JUnit 5 (Jupiter) + AssertJ, Micrometer, SLF4J API, Spring Boot 3.3.13 (starter module only), ArchUnit 1.4.2, GitHub Actions.

## Global Constraints

- Java 21 (LTS); `maven.compiler.release=21` set once in the root POM.
- Maven groupId for every module: `com.acme.flags` (design doc's placeholder, kept as-is per decision).
- `flags-api`'s `com.acme.flags.api` and `com.acme.flags.spi` packages may only import `com.acme.flags..`, `io.micrometer..`, `org.slf4j..`, or `java..` — mechanically enforced by an ArchUnit test, not just convention.
- No engine adapters (`flags-engine-unleash`, `flags-engine-goff`) and no ADR 0002 amendments (B1 governance job, B2 interaction scan, B3 resilience contract) in this pass — both are explicitly out of scope, blocked on ADR 0002 ratification.
- Test stack for every module: JUnit 5 (Jupiter) + AssertJ. No other test library.
- CI (`.github/workflows/ci.yml`) runs `mvn -B verify` only — no `japicmp`/`revapi` binary-compatibility gate and no Nexus publish step in this pass (no prior release to diff against, no real Nexus target in this sandbox).
- Version: every module starts at `0.1.0-SNAPSHOT`.
- Do not add a `Co-Authored-By: Claude` trailer to any commit in this repo.

---

### Task 1: Root Maven reactor scaffolding

**Files:**
- Create: `pom.xml` (repo root)
- Create: `.gitignore` (repo root)

**Interfaces:**
- Consumes: nothing.
- Produces: a Maven reactor (`groupId=com.acme.flags`, `artifactId=flags-parent`, `version=0.1.0-SNAPSHOT`, `packaging=pom`) with an empty `<modules>` list that later tasks append to; `dependencyManagement` importing `org.springframework.boot:spring-boot-dependencies:3.3.13`; `pluginManagement` pinning `maven-compiler-plugin:3.14.0` and `maven-surefire-plugin:3.5.3`; property `archunit.version=1.4.2` for later use.

This task is pure scaffolding — there is no failing test to write first, since no source code exists yet. The verification step is that the empty reactor validates cleanly.

- [ ] **Step 1: Write the root `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.acme.flags</groupId>
    <artifactId>flags-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <name>Flags Facade Parent</name>
    <description>Parent reactor for the in-house feature-flag facade (ADR 0001 "alongside" pattern).</description>

    <modules>
    </modules>

    <properties>
        <maven.compiler.release>21</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.3.13</spring-boot.version>
        <archunit.version>1.4.2</archunit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.14.0</version>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.3</version>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 2: Write `.gitignore`**

```
target/
*.class
.idea/
*.iml
```

- [ ] **Step 3: Verify the empty reactor validates**

Run: `mvn -q validate`
Expected: no output, exit code 0 (an empty `pom`-packaging reactor validates trivially).

- [ ] **Step 4: Commit**

```bash
git add pom.xml .gitignore
git commit -m "Add root Maven reactor for the flags facade"
```

---

### Task 2: `flags-api` — data model types

**Files:**
- Modify: `pom.xml` (add `<module>flags-api</module>`)
- Create: `flags-api/pom.xml`
- Create: `flags-api/src/main/java/com/acme/flags/api/FlagKey.java`
- Create: `flags-api/src/main/java/com/acme/flags/api/FailureSemantics.java`
- Create: `flags-api/src/main/java/com/acme/flags/api/FlagMetadata.java`
- Create: `flags-api/src/main/java/com/acme/flags/api/ConfigKey.java`
- Create: `flags-api/src/main/java/com/acme/flags/api/FlagContext.java`
- Test: `flags-api/src/test/java/com/acme/flags/api/FlagMetadataTest.java`
- Test: `flags-api/src/test/java/com/acme/flags/api/FlagContextTest.java`

**Interfaces:**
- Consumes: nothing (first module).
- Produces: `FlagKey` (`String key()`, `FlagMetadata metadata()`), `FailureSemantics` (`FAIL_OPEN`, `FAIL_CLOSED`), `FlagMetadata` (record: `owner`, `ticket`, `defaultValue`, `failureSemantics`, `expiresAfter`, with `FlagMetadata.builder()`), `ConfigKey<T>` (`Class<T> type()`, `T defaultValue()`, extends `FlagKey`), `FlagContext` (`FlagContext.forTenant(String)`, `FlagContext.anonymous()`, `FlagContext.current()`, `tenantId()`, `userId()`, `toMap()`).

- [ ] **Step 1: Modify root `pom.xml` to register the module**

In `pom.xml`, change:
```xml
    <modules>
    </modules>
```
to:
```xml
    <modules>
        <module>flags-api</module>
    </modules>
```

- [ ] **Step 2: Create `flags-api/pom.xml`**

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

    <artifactId>flags-api</artifactId>
    <packaging>jar</packaging>
    <name>Flags API</name>
    <description>Public FeatureFlags contract, engine-agnostic SPI, and the DefaultFeatureFlags reference implementation.</description>

    <dependencies>
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
</project>
```

- [ ] **Step 3: Write `FlagKey.java` and `FailureSemantics.java` (pure contracts, no logic to test)**

```java
package com.acme.flags.api;

public interface FlagKey {
    String key();

    FlagMetadata metadata();
}
```

```java
package com.acme.flags.api;

public enum FailureSemantics {
    FAIL_OPEN,
    FAIL_CLOSED
}
```

- [ ] **Step 4: Write the failing test for `FlagMetadata`**

```java
package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class FlagMetadataTest {

    @Test
    void builder_buildsMetadataWithAllFieldsSet() {
        FlagMetadata metadata = FlagMetadata.builder()
                .owner("checkout-team")
                .ticket("CHECKOUT-1421")
                .defaultValue(false)
                .failureSemantics(FailureSemantics.FAIL_CLOSED)
                .expiresAfter(Duration.ofDays(90))
                .build();

        assertThat(metadata.owner()).isEqualTo("checkout-team");
        assertThat(metadata.ticket()).isEqualTo("CHECKOUT-1421");
        assertThat(metadata.defaultValue()).isFalse();
        assertThat(metadata.failureSemantics()).isEqualTo(FailureSemantics.FAIL_CLOSED);
        assertThat(metadata.expiresAfter()).isEqualTo(Duration.ofDays(90));
    }

    @Test
    void builder_allowsNullExpiresAfter_forPermanentConfig() {
        FlagMetadata metadata = FlagMetadata.builder()
                .owner("billing-team")
                .ticket("BILL-88")
                .defaultValue(true)
                .failureSemantics(FailureSemantics.FAIL_OPEN)
                .build();

        assertThat(metadata.expiresAfter()).isNull();
    }

    @Test
    void constructor_rejectsNullOwner() {
        assertThatThrownBy(() -> new FlagMetadata(null, "T-1", false, FailureSemantics.FAIL_OPEN, null))
                .isInstanceOf(NullPointerException.class);
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `mvn -q -pl flags-api test`
Expected: FAIL — compile error, `cannot find symbol: class FlagMetadata`.

- [ ] **Step 6: Write `FlagMetadata.java`**

```java
package com.acme.flags.api;

import java.time.Duration;
import java.util.Objects;

public record FlagMetadata(
        String owner,
        String ticket,
        boolean defaultValue,
        FailureSemantics failureSemantics,
        Duration expiresAfter
) {
    public FlagMetadata {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(failureSemantics, "failureSemantics must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String owner;
        private String ticket;
        private boolean defaultValue;
        private FailureSemantics failureSemantics;
        private Duration expiresAfter;

        private Builder() {
        }

        public Builder owner(String owner) {
            this.owner = owner;
            return this;
        }

        public Builder ticket(String ticket) {
            this.ticket = ticket;
            return this;
        }

        public Builder defaultValue(boolean defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public Builder failureSemantics(FailureSemantics failureSemantics) {
            this.failureSemantics = failureSemantics;
            return this;
        }

        public Builder expiresAfter(Duration expiresAfter) {
            this.expiresAfter = expiresAfter;
            return this;
        }

        public FlagMetadata build() {
            return new FlagMetadata(owner, ticket, defaultValue, failureSemantics, expiresAfter);
        }
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q -pl flags-api test`
Expected: no output, exit code 0 (3 tests pass).

- [ ] **Step 8: Write `ConfigKey.java` (pure contract, no logic to test standalone)**

```java
package com.acme.flags.api;

public interface ConfigKey<T> extends FlagKey {
    Class<T> type();

    T defaultValue();
}
```

- [ ] **Step 9: Write the failing test for `FlagContext`**

```java
package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class FlagContextTest {

    @Test
    void anonymous_hasNoTenantOrUserAndEmptyTraits() {
        FlagContext context = FlagContext.anonymous();

        assertThat(context.tenantId()).isNull();
        assertThat(context.userId()).isNull();
        assertThat(context.toMap()).isEmpty();
    }

    @Test
    void forTenant_populatesTenantIdInMap() {
        FlagContext context = FlagContext.forTenant("tenant-42");

        assertThat(context.tenantId()).isEqualTo("tenant-42");
        assertThat(context.toMap()).isEqualTo(Map.of("tenantId", "tenant-42"));
    }

    @Test
    void forTenant_rejectsNullTenantId() {
        assertThatThrownBy(() -> FlagContext.forTenant(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void current_returnsAnonymousContext() {
        assertThat(FlagContext.current()).isEqualTo(FlagContext.anonymous());
    }
}
```

- [ ] **Step 10: Run the test to verify it fails**

Run: `mvn -q -pl flags-api test`
Expected: FAIL — compile error, `cannot find symbol: class FlagContext`.

- [ ] **Step 11: Write `FlagContext.java`**

`current()` has no web-framework integration in this phase (`flags-api` takes no Spring dependency by design) — it returns `anonymous()` until a caller supplies an explicit context. This is a deliberate, tested simplification, not a stub: there is no real tenant/security model anywhere in this repo yet for it to resolve.

```java
package com.acme.flags.api;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class FlagContext {

    private static final FlagContext ANONYMOUS = new FlagContext(null, null, Map.of());

    private final String tenantId;
    private final String userId;
    private final Map<String, String> traits;

    private FlagContext(String tenantId, String userId, Map<String, String> traits) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.traits = Map.copyOf(traits);
    }

    public static FlagContext forTenant(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return new FlagContext(tenantId, null, Map.of());
    }

    public static FlagContext anonymous() {
        return ANONYMOUS;
    }

    // No web-framework integration exists yet; returns anonymous() until a real caller-supplied context exists.
    public static FlagContext current() {
        return ANONYMOUS;
    }

    public String tenantId() {
        return tenantId;
    }

    public String userId() {
        return userId;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>(traits);
        if (tenantId != null) {
            map.put("tenantId", tenantId);
        }
        if (userId != null) {
            map.put("userId", userId);
        }
        return Map.copyOf(map);
    }
}
```

- [ ] **Step 12: Run the test to verify it passes**

Run: `mvn -q -pl flags-api test`
Expected: no output, exit code 0 (7 tests pass: 3 from `FlagMetadataTest`, 4 from `FlagContextTest`).

- [ ] **Step 13: Commit**

```bash
git add pom.xml flags-api
git commit -m "Add flags-api data model: FlagKey, FailureSemantics, FlagMetadata, ConfigKey, FlagContext"
```

---

### Task 3: `flags-api` — `FeatureFlags`, `FlagEngine` SPI, `DefaultFeatureFlags`, ArchUnit guardrail

**Files:**
- Modify: `flags-api/pom.xml` (add Micrometer, SLF4J, ArchUnit dependencies)
- Create: `flags-api/src/main/java/com/acme/flags/api/FeatureFlags.java`
- Create: `flags-api/src/main/java/com/acme/flags/spi/FlagEngine.java`
- Create: `flags-api/src/main/java/com/acme/flags/api/DefaultFeatureFlags.java`
- Create: `flags-api/src/test/java/com/acme/flags/api/MockFlagEngine.java`
- Test: `flags-api/src/test/java/com/acme/flags/api/DefaultFeatureFlagsTest.java`
- Test: `flags-api/src/test/java/com/acme/flags/api/VendorFreedomArchTest.java`

**Interfaces:**
- Consumes: `FlagKey`, `FlagMetadata`, `FailureSemantics`, `ConfigKey<T>`, `FlagContext` (Task 2).
- Produces: `FeatureFlags` (`boolean isEnabled(FlagKey, FlagContext)`, `<T> T getVariant(FlagKey, Class<T>, T, FlagContext)`, `<T> T getConfigValue(ConfigKey<T>, FlagContext)`, plus 3 no-context default overloads), `com.acme.flags.spi.FlagEngine` (`boolean evaluateBoolean(String, Map<String,String>, boolean)`, `<T> T evaluateVariant(String, Map<String,String>, Class<T>, T)`), `DefaultFeatureFlags` (constructor `(FlagEngine, MeterRegistry)`, implements `FeatureFlags`).

- [ ] **Step 1: Modify `flags-api/pom.xml` to add the new dependencies**

Add inside `<dependencies>`, above the existing `junit-jupiter`/`assertj-core` test dependencies:

```xml
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-core</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
```

Add a new test dependency after `assertj-core`:

```xml
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Write `FeatureFlags.java` and `com/acme/flags/spi/FlagEngine.java` (pure contracts)**

```java
package com.acme.flags.api;

public interface FeatureFlags {

    boolean isEnabled(FlagKey flag, FlagContext context);

    <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue, FlagContext context);

    <T> T getConfigValue(ConfigKey<T> key, FlagContext context);

    default boolean isEnabled(FlagKey flag) {
        return isEnabled(flag, FlagContext.current());
    }

    default <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue) {
        return getVariant(flag, type, defaultValue, FlagContext.current());
    }

    default <T> T getConfigValue(ConfigKey<T> key) {
        return getConfigValue(key, FlagContext.current());
    }
}
```

```java
package com.acme.flags.spi;

import java.util.Map;

public interface FlagEngine {

    boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue);

    <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue);
}
```

- [ ] **Step 3: Write the `MockFlagEngine` test double**

```java
package com.acme.flags.api;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

final class MockFlagEngine implements FlagEngine {

    private boolean throwOnEvaluate = false;
    private Boolean fixedBooleanResult;
    private Object fixedVariantResult;

    void throwOnEvaluate() {
        this.throwOnEvaluate = true;
    }

    void returnBoolean(boolean value) {
        this.fixedBooleanResult = value;
    }

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        if (throwOnEvaluate) {
            throw new RuntimeException("engine unreachable");
        }
        return fixedBooleanResult != null ? fixedBooleanResult : defaultValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        if (throwOnEvaluate) {
            throw new RuntimeException("engine unreachable");
        }
        return fixedVariantResult != null ? (T) fixedVariantResult : defaultValue;
    }
}
```

- [ ] **Step 4: Write the failing test for `DefaultFeatureFlags`**

```java
package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultFeatureFlagsTest {

    private static final FlagKey BOOL_FLAG = new TestFlagKey("test.bool-flag",
            FlagMetadata.builder()
                    .owner("test-team")
                    .ticket("TEST-1")
                    .defaultValue(false)
                    .failureSemantics(FailureSemantics.FAIL_CLOSED)
                    .expiresAfter(Duration.ofDays(90))
                    .build());

    private MockFlagEngine engine;
    private SimpleMeterRegistry metrics;
    private DefaultFeatureFlags flags;

    @BeforeEach
    void setUp() {
        engine = new MockFlagEngine();
        metrics = new SimpleMeterRegistry();
        flags = new DefaultFeatureFlags(engine, metrics);
    }

    @Test
    void isEnabled_returnsEngineResult_whenEngineSucceeds() {
        engine.returnBoolean(true);

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous())).isTrue();
    }

    @Test
    void isEnabled_fallsBackToMetadataDefault_whenEngineThrows() {
        engine.throwOnEvaluate();

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous()))
                .isEqualTo(BOOL_FLAG.metadata().defaultValue());
    }

    @Test
    void isEnabled_recordsMetric_withFlagKeyAndResult() {
        engine.returnBoolean(true);

        flags.isEnabled(BOOL_FLAG, FlagContext.anonymous());

        double count = metrics.counter("feature_flag.evaluated", "flag", BOOL_FLAG.key(), "result", "true").count();
        assertThat(count).isEqualTo(1.0);
    }

    @Test
    void getVariant_fallsBackToSuppliedDefault_whenEngineThrows() {
        engine.throwOnEvaluate();

        String result = flags.getVariant(BOOL_FLAG, String.class, "fallback", FlagContext.anonymous());

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void isEnabled_noContextOverload_resolvesCurrentContext() {
        engine.returnBoolean(true);

        assertThat(flags.isEnabled(BOOL_FLAG)).isTrue();
    }

    private record TestFlagKey(String key, FlagMetadata metadata) implements FlagKey {
    }
}
```

- [ ] **Step 5: Run the test to verify it fails**

Run: `mvn -q -pl flags-api test`
Expected: FAIL — compile error, `cannot find symbol: class DefaultFeatureFlags`.

- [ ] **Step 6: Write `DefaultFeatureFlags.java`**

```java
package com.acme.flags.api;

import com.acme.flags.spi.FlagEngine;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultFeatureFlags implements FeatureFlags {

    private static final Logger log = LoggerFactory.getLogger(DefaultFeatureFlags.class);

    private final FlagEngine engine;
    private final MeterRegistry metrics;

    public DefaultFeatureFlags(FlagEngine engine, MeterRegistry metrics) {
        this.engine = engine;
        this.metrics = metrics;
    }

    @Override
    public boolean isEnabled(FlagKey flag, FlagContext context) {
        boolean defaultValue = flag.metadata().defaultValue();
        boolean result;
        try {
            result = engine.evaluateBoolean(flag.key(), context.toMap(), defaultValue);
        } catch (RuntimeException e) {
            result = defaultValue;
            log.warn("Flag engine unavailable, falling back to default value for {}", flag.key(), e);
        }
        recordEvaluation(flag.key(), result);
        return result;
    }

    @Override
    public <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue, FlagContext context) {
        T result;
        try {
            result = engine.evaluateVariant(flag.key(), context.toMap(), type, defaultValue);
        } catch (RuntimeException e) {
            result = defaultValue;
            log.warn("Flag engine unavailable, falling back to default value for {}", flag.key(), e);
        }
        recordEvaluation(flag.key(), result);
        return result;
    }

    @Override
    public <T> T getConfigValue(ConfigKey<T> key, FlagContext context) {
        return getVariant(key, key.type(), key.defaultValue(), context);
    }

    private void recordEvaluation(String flagKey, Object result) {
        metrics.counter("feature_flag.evaluated", "flag", flagKey, "result", String.valueOf(result)).increment();
    }
}
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `mvn -q -pl flags-api test`
Expected: no output, exit code 0 (12 tests pass: 7 from Task 2 + 5 from `DefaultFeatureFlagsTest`).

- [ ] **Step 8: Write the ArchUnit guardrail test**

```java
package com.acme.flags.api;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class VendorFreedomArchTest {

    @Test
    void apiAndSpiPackagesOnlyDependOnAllowedPackages() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.acme.flags");

        ArchRule rule = classes()
                .that().resideInAnyPackage("com.acme.flags.api", "com.acme.flags.spi")
                .should().onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "com.acme.flags.api",
                        "com.acme.flags.spi",
                        "io.micrometer..",
                        "org.slf4j..",
                        "java..");

        rule.check(classes);
    }
}
```

- [ ] **Step 9: Run the full test suite for this module**

Run: `mvn -q -pl flags-api test`
Expected: no output, exit code 0 (13 tests pass — the ArchUnit rule passes because `flags-api`'s main classes only import `java..`, `io.micrometer..`, and `org.slf4j..`).

- [ ] **Step 10: Commit**

```bash
git add flags-api
git commit -m "Add FeatureFlags, FlagEngine SPI, DefaultFeatureFlags, and a vendor-freedom ArchUnit guardrail"
```

---

### Task 4: `flags-contract-test` — shared `FlagEngine` conformance suite

**Files:**
- Modify: `pom.xml` (add `<module>flags-contract-test</module>`)
- Create: `flags-contract-test/pom.xml`
- Create: `flags-contract-test/src/main/java/com/acme/flags/contracttest/FlagEngineContractTest.java`
- Test: `flags-contract-test/src/test/java/com/acme/flags/contracttest/AlwaysDefaultFlagEngineTest.java`

**Interfaces:**
- Consumes: `com.acme.flags.spi.FlagEngine` (Task 3).
- Produces: `public abstract class FlagEngineContractTest` with `protected abstract FlagEngine engine()` and three **public** `@Test` methods — `unknownKeyReturnsDefault()`, `nullContextTreatedAsEmpty()`, `concurrentEvaluationIsSafe()` — public so they are inherited correctly by subclasses in other modules' packages (package-private methods are not inherited across packages in Java).

- [ ] **Step 1: Modify root `pom.xml` to register the module**

```xml
    <modules>
        <module>flags-api</module>
        <module>flags-contract-test</module>
    </modules>
```

- [ ] **Step 2: Create `flags-contract-test/pom.xml`**

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

    <artifactId>flags-contract-test</artifactId>
    <packaging>jar</packaging>
    <name>Flags Contract Test</name>
    <description>Abstract JUnit suite every FlagEngine implementation must pass.</description>

    <dependencies>
        <dependency>
            <groupId>com.acme.flags</groupId>
            <artifactId>flags-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.assertj</groupId>
            <artifactId>assertj-core</artifactId>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Write the failing self-test**

```java
package com.acme.flags.contracttest;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

class AlwaysDefaultFlagEngineTest extends FlagEngineContractTest {

    @Override
    protected FlagEngine engine() {
        return new FlagEngine() {
            @Override
            public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
                return defaultValue;
            }

            @Override
            public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
                return defaultValue;
            }
        };
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -q -pl flags-contract-test -am test`
Expected: FAIL — compile error, `cannot find symbol: class FlagEngineContractTest`.

- [ ] **Step 5: Write `FlagEngineContractTest.java`**

```java
package com.acme.flags.contracttest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.acme.flags.spi.FlagEngine;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public abstract class FlagEngineContractTest {

    protected abstract FlagEngine engine();

    @Test
    public void unknownKeyReturnsDefault() {
        assertThat(engine().evaluateBoolean("does-not-exist", Map.of(), true)).isTrue();
        assertThat(engine().evaluateBoolean("does-not-exist", Map.of(), false)).isFalse();
    }

    @Test
    public void nullContextTreatedAsEmpty() {
        assertThatCode(() -> engine().evaluateBoolean("any-key", null, false)).doesNotThrowAnyException();
    }

    @Test
    public void concurrentEvaluationIsSafe() throws Exception {
        FlagEngine target = engine();
        int threadCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, threadCount)
                    .<Callable<Boolean>>mapToObj(i -> () -> target.evaluateBoolean("concurrent-key", Map.of(), false))
                    .toList();
            List<Future<Boolean>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            for (Future<Boolean> future : futures) {
                assertThat(future.get()).isFalse();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl flags-contract-test -am test`
Expected: no output, exit code 0 (3 tests pass, inherited from `FlagEngineContractTest` by `AlwaysDefaultFlagEngineTest`).

- [ ] **Step 7: Commit**

```bash
git add pom.xml flags-contract-test
git commit -m "Add flags-contract-test: the shared FlagEngine conformance suite"
```

---

### Task 5: `flags-noop` — `NoOpFlagEngine`, `InMemoryFlagEngine`, `FlagOverridesProperties`

**Files:**
- Modify: `pom.xml` (add `<module>flags-noop</module>`)
- Create: `flags-noop/pom.xml`
- Create: `flags-noop/src/main/java/com/acme/flags/noop/NoOpFlagEngine.java`
- Create: `flags-noop/src/main/java/com/acme/flags/noop/FlagOverridesProperties.java`
- Create: `flags-noop/src/main/java/com/acme/flags/noop/InMemoryFlagEngine.java`
- Test: `flags-noop/src/test/java/com/acme/flags/noop/NoOpFlagEngineTest.java`
- Test: `flags-noop/src/test/java/com/acme/flags/noop/InMemoryFlagEngineTest.java`
- Test: `flags-noop/src/test/java/com/acme/flags/noop/InMemoryFlagEngineOverrideTest.java`

**Interfaces:**
- Consumes: `com.acme.flags.spi.FlagEngine` (Task 3), `com.acme.flags.contracttest.FlagEngineContractTest` (Task 4).
- Produces: `NoOpFlagEngine` (no-arg constructor), `FlagOverridesProperties` (`Map<String,String> getOverrides()`, Spring `@ConfigurationProperties(prefix = "flags")`), `InMemoryFlagEngine` (constructor `(FlagOverridesProperties)`).

- [ ] **Step 1: Modify root `pom.xml` to register the module**

```xml
    <modules>
        <module>flags-api</module>
        <module>flags-contract-test</module>
        <module>flags-noop</module>
    </modules>
```

- [ ] **Step 2: Create `flags-noop/pom.xml`**

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

    <artifactId>flags-noop</artifactId>
    <packaging>jar</packaging>
    <name>Flags NoOp/InMemory Engines</name>
    <description>Reference FlagEngine implementations that ship without any vendor backend.</description>

    <dependencies>
        <dependency>
            <groupId>com.acme.flags</groupId>
            <artifactId>flags-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot</artifactId>
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
        <dependency>
            <groupId>com.acme.flags</groupId>
            <artifactId>flags-contract-test</artifactId>
            <version>${project.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Write the failing test for `NoOpFlagEngine`**

```java
package com.acme.flags.noop;

import com.acme.flags.contracttest.FlagEngineContractTest;
import com.acme.flags.spi.FlagEngine;

class NoOpFlagEngineTest extends FlagEngineContractTest {

    @Override
    protected FlagEngine engine() {
        return new NoOpFlagEngine();
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -q -pl flags-noop -am test`
Expected: FAIL — compile error, `cannot find symbol: class NoOpFlagEngine`.

- [ ] **Step 5: Write `NoOpFlagEngine.java`**

```java
package com.acme.flags.noop;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

public final class NoOpFlagEngine implements FlagEngine {

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        return defaultValue;
    }

    @Override
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        return defaultValue;
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl flags-noop -am test`
Expected: no output, exit code 0 (3 tests pass, inherited from `FlagEngineContractTest`).

- [ ] **Step 7: Write the failing tests for `InMemoryFlagEngine`**

```java
package com.acme.flags.noop;

import com.acme.flags.contracttest.FlagEngineContractTest;
import com.acme.flags.spi.FlagEngine;

class InMemoryFlagEngineTest extends FlagEngineContractTest {

    @Override
    protected FlagEngine engine() {
        return new InMemoryFlagEngine(new FlagOverridesProperties());
    }
}
```

```java
package com.acme.flags.noop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class InMemoryFlagEngineOverrideTest {

    @Test
    void evaluateBoolean_returnsConfiguredOverride_whenPresent() {
        FlagOverridesProperties properties = new FlagOverridesProperties();
        properties.getOverrides().put("checkout.new-checkout-flow", "true");
        InMemoryFlagEngine engine = new InMemoryFlagEngine(properties);

        assertThat(engine.evaluateBoolean("checkout.new-checkout-flow", Map.of(), false)).isTrue();
    }

    @Test
    void evaluateVariant_parsesStringOverrideToRequestedType() {
        FlagOverridesProperties properties = new FlagOverridesProperties();
        properties.getOverrides().put("billing.max-retries", "5");
        InMemoryFlagEngine engine = new InMemoryFlagEngine(properties);

        Integer result = engine.evaluateVariant("billing.max-retries", Map.of(), Integer.class, 3);

        assertThat(result).isEqualTo(5);
    }
}
```

- [ ] **Step 8: Run the tests to verify they fail**

Run: `mvn -q -pl flags-noop -am test`
Expected: FAIL — compile error, `cannot find symbol: class FlagOverridesProperties` / `class InMemoryFlagEngine`.

- [ ] **Step 9: Write `FlagOverridesProperties.java`**

```java
package com.acme.flags.noop;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "flags")
public class FlagOverridesProperties {

    private final Map<String, String> overrides = new LinkedHashMap<>();

    public Map<String, String> getOverrides() {
        return overrides;
    }
}
```

- [ ] **Step 10: Write `InMemoryFlagEngine.java`**

```java
package com.acme.flags.noop;

import com.acme.flags.spi.FlagEngine;
import java.util.Map;

public final class InMemoryFlagEngine implements FlagEngine {

    private final FlagOverridesProperties properties;

    public InMemoryFlagEngine(FlagOverridesProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        String override = properties.getOverrides().get(key);
        return override == null ? defaultValue : Boolean.parseBoolean(override);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        String override = properties.getOverrides().get(key);
        if (override == null) {
            return defaultValue;
        }
        if (type == String.class) {
            return (T) override;
        }
        if (type == Boolean.class) {
            return (T) Boolean.valueOf(override);
        }
        if (type == Integer.class) {
            return (T) Integer.valueOf(override);
        }
        if (type == Long.class) {
            return (T) Long.valueOf(override);
        }
        if (type == Double.class) {
            return (T) Double.valueOf(override);
        }
        return defaultValue;
    }
}
```

- [ ] **Step 11: Run the full test suite for this module**

Run: `mvn -q -pl flags-noop -am test`
Expected: no output, exit code 0 (8 tests pass: 3 from `NoOpFlagEngineTest`, 3 from `InMemoryFlagEngineTest`, 2 from `InMemoryFlagEngineOverrideTest`).

- [ ] **Step 12: Commit**

```bash
git add pom.xml flags-noop
git commit -m "Add flags-noop: NoOpFlagEngine and InMemoryFlagEngine"
```

---

### Task 6: `flags-test-fixtures` — `FakeFeatureFlags`

**Files:**
- Modify: `pom.xml` (add `<module>flags-test-fixtures</module>`)
- Create: `flags-test-fixtures/pom.xml`
- Create: `flags-test-fixtures/src/main/java/com/acme/flags/testfixtures/FakeFeatureFlags.java`
- Test: `flags-test-fixtures/src/test/java/com/acme/flags/testfixtures/FakeFeatureFlagsTest.java`

**Interfaces:**
- Consumes: `FeatureFlags`, `FlagKey`, `FlagContext`, `ConfigKey<T>` (Tasks 2–3).
- Produces: `FakeFeatureFlags` (implements `FeatureFlags`; `void set(FlagKey, boolean)`, `<T> void set(FlagKey, T)`, `void clear()`).

- [ ] **Step 1: Modify root `pom.xml` to register the module**

```xml
    <modules>
        <module>flags-api</module>
        <module>flags-contract-test</module>
        <module>flags-noop</module>
        <module>flags-test-fixtures</module>
    </modules>
```

- [ ] **Step 2: Create `flags-test-fixtures/pom.xml`**

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

    <artifactId>flags-test-fixtures</artifactId>
    <packaging>jar</packaging>
    <name>Flags Test Fixtures</name>
    <description>FakeFeatureFlags test double for consumers of the facade.</description>

    <dependencies>
        <dependency>
            <groupId>com.acme.flags</groupId>
            <artifactId>flags-api</artifactId>
            <version>${project.version}</version>
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
</project>
```

- [ ] **Step 3: Write the failing test for `FakeFeatureFlags`**

```java
package com.acme.flags.testfixtures;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.flags.api.FailureSemantics;
import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagKey;
import com.acme.flags.api.FlagMetadata;
import org.junit.jupiter.api.Test;

class FakeFeatureFlagsTest {

    private static final FlagKey BOOL_FLAG = new TestFlagKey("test.bool-flag",
            FlagMetadata.builder()
                    .owner("test-team")
                    .ticket("TEST-1")
                    .defaultValue(false)
                    .failureSemantics(FailureSemantics.FAIL_CLOSED)
                    .build());

    @Test
    void isEnabled_returnsMetadataDefault_whenNotOverridden() {
        FakeFeatureFlags flags = new FakeFeatureFlags();

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous())).isFalse();
    }

    @Test
    void isEnabled_returnsOverriddenValue_whenSet() {
        FakeFeatureFlags flags = new FakeFeatureFlags();
        flags.set(BOOL_FLAG, true);

        assertThat(flags.isEnabled(BOOL_FLAG, FlagContext.anonymous())).isTrue();
    }

    @Test
    void getVariant_returnsSuppliedDefault_whenNotOverridden() {
        FakeFeatureFlags flags = new FakeFeatureFlags();

        String result = flags.getVariant(BOOL_FLAG, String.class, "fallback", FlagContext.anonymous());

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void getVariant_returnsOverriddenValue_whenSet() {
        FakeFeatureFlags flags = new FakeFeatureFlags();
        flags.set(BOOL_FLAG, "override-value");

        String result = flags.getVariant(BOOL_FLAG, String.class, "fallback", FlagContext.anonymous());

        assertThat(result).isEqualTo("override-value");
    }

    private record TestFlagKey(String key, FlagMetadata metadata) implements FlagKey {
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -q -pl flags-test-fixtures -am test`
Expected: FAIL — compile error, `cannot find symbol: class FakeFeatureFlags`.

- [ ] **Step 5: Write `FakeFeatureFlags.java`**

```java
package com.acme.flags.testfixtures;

import com.acme.flags.api.ConfigKey;
import com.acme.flags.api.FeatureFlags;
import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagKey;
import java.util.HashMap;
import java.util.Map;

public final class FakeFeatureFlags implements FeatureFlags {

    private final Map<String, Object> overrides = new HashMap<>();

    public void set(FlagKey flag, boolean value) {
        overrides.put(flag.key(), value);
    }

    public <T> void set(FlagKey flag, T value) {
        overrides.put(flag.key(), value);
    }

    public void clear() {
        overrides.clear();
    }

    @Override
    public boolean isEnabled(FlagKey flag, FlagContext context) {
        Object override = overrides.get(flag.key());
        return override instanceof Boolean value ? value : flag.metadata().defaultValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getVariant(FlagKey flag, Class<T> type, T defaultValue, FlagContext context) {
        Object override = overrides.get(flag.key());
        return type.isInstance(override) ? (T) override : defaultValue;
    }

    @Override
    public <T> T getConfigValue(ConfigKey<T> key, FlagContext context) {
        return getVariant(key, key.type(), key.defaultValue(), context);
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl flags-test-fixtures -am test`
Expected: no output, exit code 0 (4 tests pass).

- [ ] **Step 7: Commit**

```bash
git add pom.xml flags-test-fixtures
git commit -m "Add flags-test-fixtures: FakeFeatureFlags"
```

---

### Task 7: `flags-spring-boot-starter` — auto-configuration

**Files:**
- Modify: `pom.xml` (add `<module>flags-spring-boot-starter</module>`)
- Create: `flags-spring-boot-starter/pom.xml`
- Create: `flags-spring-boot-starter/src/main/java/com/acme/flags/springboot/FlagsAutoConfiguration.java`
- Create: `flags-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `flags-spring-boot-starter/src/test/java/com/acme/flags/springboot/FlagsAutoConfigurationTest.java`
- Test: `flags-spring-boot-starter/src/test/java/com/acme/flags/springboot/AutoConfigurationImportsFileTest.java`

**Interfaces:**
- Consumes: `FeatureFlags`, `DefaultFeatureFlags` (Task 3), `NoOpFlagEngine`, `InMemoryFlagEngine`, `FlagOverridesProperties` (Task 5).
- Produces: `FlagsAutoConfiguration` — a Spring Boot `@AutoConfiguration` class providing a `FeatureFlags` bean, selecting `NoOpFlagEngine` (default, `flags.engine` unset or `noop`) or `InMemoryFlagEngine` (`flags.engine=in-memory`), plus a fallback `SimpleMeterRegistry` bean when none exists.

- [ ] **Step 1: Modify root `pom.xml` to register the module**

```xml
    <modules>
        <module>flags-api</module>
        <module>flags-contract-test</module>
        <module>flags-noop</module>
        <module>flags-test-fixtures</module>
        <module>flags-spring-boot-starter</module>
    </modules>
```

- [ ] **Step 2: Create `flags-spring-boot-starter/pom.xml`**

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

    <artifactId>flags-spring-boot-starter</artifactId>
    <packaging>jar</packaging>
    <name>Flags Spring Boot Starter</name>
    <description>Auto-configuration wiring a FeatureFlags bean from the flags.engine property.</description>

    <dependencies>
        <dependency>
            <groupId>com.acme.flags</groupId>
            <artifactId>flags-api</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>com.acme.flags</groupId>
            <artifactId>flags-noop</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-autoconfigure</artifactId>
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
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 3: Write the failing test for `FlagsAutoConfiguration`**

```java
package com.acme.flags.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.flags.api.FeatureFlags;
import com.acme.flags.noop.InMemoryFlagEngine;
import com.acme.flags.noop.NoOpFlagEngine;
import com.acme.flags.spi.FlagEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FlagsAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(FlagsAutoConfiguration.class));

    @Test
    void defaultsToNoOpEngineAndProvidesFeatureFlagsBean() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FeatureFlags.class);
            assertThat(context).hasSingleBean(FlagEngine.class);
            assertThat(context.getBean(FlagEngine.class)).isInstanceOf(NoOpFlagEngine.class);
        });
    }

    @Test
    void wiresInMemoryEngine_whenPropertySet() {
        contextRunner
                .withPropertyValues("flags.engine=in-memory", "flags.overrides.checkout.new-checkout-flow=true")
                .run(context -> {
                    assertThat(context.getBean(FlagEngine.class)).isInstanceOf(InMemoryFlagEngine.class);
                    assertThat(context).hasSingleBean(FeatureFlags.class);
                });
    }
}
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `mvn -q -pl flags-spring-boot-starter -am test`
Expected: FAIL — compile error, `cannot find symbol: class FlagsAutoConfiguration`.

- [ ] **Step 5: Write `FlagsAutoConfiguration.java`**

```java
package com.acme.flags.springboot;

import com.acme.flags.api.DefaultFeatureFlags;
import com.acme.flags.api.FeatureFlags;
import com.acme.flags.noop.FlagOverridesProperties;
import com.acme.flags.noop.InMemoryFlagEngine;
import com.acme.flags.noop.NoOpFlagEngine;
import com.acme.flags.spi.FlagEngine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(FlagOverridesProperties.class)
public class FlagsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry flagsMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    @ConditionalOnMissingBean(FlagEngine.class)
    @ConditionalOnProperty(name = "flags.engine", havingValue = "in-memory")
    public FlagEngine inMemoryFlagEngine(FlagOverridesProperties properties) {
        return new InMemoryFlagEngine(properties);
    }

    @Bean
    @ConditionalOnMissingBean(FlagEngine.class)
    @ConditionalOnProperty(name = "flags.engine", havingValue = "noop", matchIfMissing = true)
    public FlagEngine noOpFlagEngine() {
        return new NoOpFlagEngine();
    }

    @Bean
    @ConditionalOnMissingBean(FeatureFlags.class)
    public FeatureFlags featureFlags(FlagEngine engine, MeterRegistry metrics) {
        return new DefaultFeatureFlags(engine, metrics);
    }
}
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `mvn -q -pl flags-spring-boot-starter -am test`
Expected: no output, exit code 0 (2 tests pass).

- [ ] **Step 7: Write the failing test for the auto-configuration imports file**

```java
package com.acme.flags.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AutoConfigurationImportsFileTest {

    @Test
    void importsFile_declaresFlagsAutoConfiguration() throws IOException {
        String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertThat(stream).as("resource %s should exist on the classpath", resource).isNotNull();
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
            assertThat(content).isEqualTo("com.acme.flags.springboot.FlagsAutoConfiguration");
        }
    }
}
```

- [ ] **Step 8: Run the test to verify it fails**

Run: `mvn -q -pl flags-spring-boot-starter -am test`
Expected: FAIL — assertion error, resource stream is null (the imports file does not exist yet).

- [ ] **Step 9: Write the imports resource file**

```
com.acme.flags.springboot.FlagsAutoConfiguration
```

- [ ] **Step 10: Run the full test suite for this module**

Run: `mvn -q -pl flags-spring-boot-starter -am test`
Expected: no output, exit code 0 (3 tests pass).

- [ ] **Step 11: Commit**

```bash
git add pom.xml flags-spring-boot-starter
git commit -m "Add flags-spring-boot-starter: FlagsAutoConfiguration"
```

---

### Task 8: `flags-bom` — version-pinning BOM

**Files:**
- Modify: `pom.xml` (add `<module>flags-bom</module>`)
- Create: `flags-bom/pom.xml`

**Interfaces:**
- Consumes: the `groupId:artifactId:${project.version}` coordinates of all 5 previously built modules.
- Produces: `flags-bom` — a `pom`-packaged artifact with `dependencyManagement` pinning `flags-api`, `flags-noop`, `flags-test-fixtures`, `flags-contract-test`, `flags-spring-boot-starter` at the same version, for consuming services to import.

- [ ] **Step 1: Modify root `pom.xml` to register the module**

```xml
    <modules>
        <module>flags-api</module>
        <module>flags-contract-test</module>
        <module>flags-noop</module>
        <module>flags-test-fixtures</module>
        <module>flags-spring-boot-starter</module>
        <module>flags-bom</module>
    </modules>
```

- [ ] **Step 2: Create `flags-bom/pom.xml`**

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

    <artifactId>flags-bom</artifactId>
    <packaging>pom</packaging>
    <name>Flags BOM</name>
    <description>Dependency-management BOM pinning consistent versions across all published flags-* artifacts.</description>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.acme.flags</groupId>
                <artifactId>flags-api</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acme.flags</groupId>
                <artifactId>flags-noop</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acme.flags</groupId>
                <artifactId>flags-test-fixtures</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acme.flags</groupId>
                <artifactId>flags-contract-test</artifactId>
                <version>${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.acme.flags</groupId>
                <artifactId>flags-spring-boot-starter</artifactId>
                <version>${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 3: Verify the whole reactor still validates structurally**

Run: `mvn -q validate`
Expected: no output, exit code 0 (all 7 POMs — root + 6 modules — are structurally valid and the reactor resolves module interdependencies correctly).

- [ ] **Step 4: Commit**

```bash
git add pom.xml flags-bom
git commit -m "Add flags-bom: version-pinning BOM for consumers"
```

---

### Task 9: CI workflow and final full-repo verification

**Files:**
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: the complete reactor from Tasks 1–8.
- Produces: a GitHub Actions workflow that runs `mvn -B verify` (compiling every module, running every unit/contract/ArchUnit test) on push and pull request.

- [ ] **Step 1: Write `.github/workflows/ci.yml`**

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Build and verify
        run: mvn -B verify
```

- [ ] **Step 2: Run the full reactor build locally, exactly as CI will**

Run: `mvn -B verify`
Expected: `BUILD SUCCESS`, with a reactor summary listing all 6 modules (`flags-api`, `flags-contract-test`, `flags-noop`, `flags-test-fixtures`, `flags-spring-boot-starter`, `flags-bom`) as `SUCCESS`, and each code module's own Surefire summary line showing `Tests run: N, Failures: 0, Errors: 0, Skipped: 0` (13 in `flags-api`, 3 in `flags-contract-test`, 8 in `flags-noop`, 4 in `flags-test-fixtures`, 3 in `flags-spring-boot-starter`).

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/ci.yml
git commit -m "Add CI workflow running mvn verify"
```
