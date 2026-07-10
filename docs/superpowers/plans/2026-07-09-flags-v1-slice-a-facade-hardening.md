# v1 Slice A — Facade Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `FlagContext.current()` against real request-scoped data (no live I/O) and narrow `DefaultFeatureFlags`'s exception handling to a dedicated `FlagEngineUnavailableException`, per [`2026-07-09-flags-v1-slice-a-facade-hardening-design.md`](../specs/2026-07-09-flags-v1-slice-a-facade-hardening-design.md).

**Architecture:** A `ThreadLocal`-backed `FlagContextHolder` in `flags-api` (vendor-free), populated by a `jakarta.servlet.Filter` in `flags-spring-boot-starter` that delegates to a consumer-supplied `FlagContextResolver` SPI (defaulting to anonymous). Separately, a new `FlagEngineUnavailableException` in `com.acme.flags.spi` narrows `DefaultFeatureFlags`'s catch clause so engine bugs propagate instead of being silently absorbed as "engine unavailable."

**Tech Stack:** Java 21, JUnit 5 (Jupiter) + AssertJ, hand-rolled test doubles (no Mockito — matches this repo's existing convention), Spring Boot 3.3.13 autoconfigure + `ApplicationContextRunner`/`WebApplicationContextRunner`.

## Global Constraints

- No Mockito or any new mocking framework — every existing test double in this repo (`MockFlagEngine`, `FakeFeatureFlags`) is hand-rolled; new tests follow the same pattern.
- `flags-api`'s `com.acme.flags.api`/`com.acme.flags.spi` packages must keep passing `VendorFreedomArchTest` (`flags-api/src/test/java/com/acme/flags/api/VendorFreedomArchTest.java`) — only `java..`, `io.micrometer..`, `org.slf4j..`, and `com.acme.flags..` imports allowed there. `flags-spring-boot-starter` is NOT covered by that rule and may depend on servlet/Spring types freely.
- Any `ThreadLocal` use must clear in a `finally` block — a leaked value on a pooled or virtual thread leaks context into the next request.
- Tests that touch `FlagContextHolder` must clear it in `@AfterEach` to avoid cross-test leakage (JUnit 5 runs test methods sequentially on one thread by default in this project — no parallel execution is configured).
- Package placement: `FlagContextHolder`, `FlagContextResolver` → `com.acme.flags.api` (flags-api); `FlagEngineUnavailableException` → `com.acme.flags.spi` (flags-api); `FlagContextFilter` → `com.acme.flags.springboot` (flags-spring-boot-starter).
- Every module's Maven coordinates stay `com.acme.flags:<artifact>:0.1.0-SNAPSHOT`.
- Commit messages: no `Co-Authored-By: Claude` trailer.

---

### Task 1: `FlagContextHolder`

**Files:**
- Create: `flags-api/src/main/java/com/acme/flags/api/FlagContextHolder.java`
- Test: `flags-api/src/test/java/com/acme/flags/api/FlagContextHolderTest.java`

**Interfaces:**
- Produces: `FlagContextHolder.set(FlagContext)`, `FlagContextHolder.get()` (returns `FlagContext` or `null`), `FlagContextHolder.clear()` — consumed by Task 2 (`FlagContext.current()`) and Task 3 (`FlagContextFilter`).

- [ ] **Step 1: Write the failing tests**

```java
package com.acme.flags.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlagContextHolderTest {

    @AfterEach
    void clear() {
        FlagContextHolder.clear();
    }

    @Test
    void get_returnsNull_whenNothingSet() {
        assertThat(FlagContextHolder.get()).isNull();
    }

    @Test
    void get_returnsSetContext() {
        FlagContext context = FlagContext.forTenant("tenant-1");

        FlagContextHolder.set(context);

        assertThat(FlagContextHolder.get()).isEqualTo(context);
    }

    @Test
    void clear_removesSetContext() {
        FlagContextHolder.set(FlagContext.forTenant("tenant-1"));

        FlagContextHolder.clear();

        assertThat(FlagContextHolder.get()).isNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl flags-api test -Dtest=FlagContextHolderTest`
Expected: FAIL — compilation error, `FlagContextHolder` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.acme.flags.api;

public final class FlagContextHolder {

    private static final ThreadLocal<FlagContext> CURRENT = new ThreadLocal<>();

    private FlagContextHolder() {
    }

    public static void set(FlagContext context) {
        CURRENT.set(context);
    }

    public static FlagContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl flags-api test -Dtest=FlagContextHolderTest`
Expected: PASS, 3/3.

- [ ] **Step 5: Commit**

```bash
git add flags-api/src/main/java/com/acme/flags/api/FlagContextHolder.java flags-api/src/test/java/com/acme/flags/api/FlagContextHolderTest.java
git commit -m "Add FlagContextHolder: ThreadLocal-backed context propagation"
```

---

### Task 2: `FlagContext.current()` reads the holder

**Files:**
- Modify: `flags-api/src/main/java/com/acme/flags/api/FlagContext.java`
- Modify: `flags-api/src/test/java/com/acme/flags/api/FlagContextTest.java`

**Interfaces:**
- Consumes: `FlagContextHolder.get()` (Task 1).
- Produces: `FlagContext.current()` now returns the held context when set, `anonymous()` otherwise — consumed by every existing `isEnabled(FlagKey)`/`getVariant(...)` no-context overload in `FeatureFlags`, and by Task 3's filter test.

- [ ] **Step 1: Write the failing test**

Add to `FlagContextTest.java` (existing file — add this method and the `@AfterEach`, keep all existing tests unchanged):

```java
    @AfterEach
    void clearContext() {
        FlagContextHolder.clear();
    }

    @Test
    void current_returnsHeldContext_whenSet() {
        FlagContext context = FlagContext.forTenant("tenant-7");
        FlagContextHolder.set(context);

        assertThat(FlagContext.current()).isEqualTo(context);
    }
```

Add `import org.junit.jupiter.api.AfterEach;` to the existing import block.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl flags-api test -Dtest=FlagContextTest#current_returnsHeldContext_whenSet`
Expected: FAIL — `current()` still returns `ANONYMOUS` regardless of the holder.

- [ ] **Step 3: Update `FlagContext.current()`**

Replace the existing method (remove the stale `// No web-framework integration exists yet...` comment):

```java
    public static FlagContext current() {
        FlagContext held = FlagContextHolder.get();
        return held != null ? held : ANONYMOUS;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl flags-api test -Dtest=FlagContextTest`
Expected: PASS, all tests including the pre-existing `current_returnsAnonymousContext` (holder unset by default, and cleared by the new `@AfterEach` after each test) and the new one.

- [ ] **Step 5: Commit**

```bash
git add flags-api/src/main/java/com/acme/flags/api/FlagContext.java flags-api/src/test/java/com/acme/flags/api/FlagContextTest.java
git commit -m "FlagContext.current() reads FlagContextHolder, falls back to anonymous"
```

---

### Task 3: `FlagContextResolver` SPI + `FlagContextFilter`

**Files:**
- Create: `flags-api/src/main/java/com/acme/flags/api/FlagContextResolver.java`
- Modify: `flags-spring-boot-starter/pom.xml`
- Create: `flags-spring-boot-starter/src/main/java/com/acme/flags/springboot/FlagContextFilter.java`
- Test: `flags-spring-boot-starter/src/test/java/com/acme/flags/springboot/FlagContextFilterTest.java`

**Interfaces:**
- Consumes: `FlagContextHolder.set/clear` (Task 1), `FlagContext.anonymous()`.
- Produces: `FlagContextResolver.resolve(): FlagContext` (implemented by consuming services; a default no-op instance is wired in Task 4), `FlagContextFilter(FlagContextResolver)` implementing `jakarta.servlet.Filter` — consumed by Task 4's autoconfiguration wiring.

- [ ] **Step 1: Add the `FlagContextResolver` interface**

```java
package com.acme.flags.api;

public interface FlagContextResolver {
    FlagContext resolve();
}
```

- [ ] **Step 2: Add the `jakarta.servlet-api` dependency**

In `flags-spring-boot-starter/pom.xml`, add immediately after the `spring-boot-autoconfigure` dependency (version is managed by the `spring-boot-dependencies` BOM already imported in the root `pom.xml` — no explicit version needed, matching how `spring-boot-autoconfigure` itself has none):

```xml
        <dependency>
            <groupId>jakarta.servlet</groupId>
            <artifactId>jakarta.servlet-api</artifactId>
            <scope>provided</scope>
        </dependency>
```

- [ ] **Step 3: Write the failing tests**

```java
package com.acme.flags.springboot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class FlagContextFilterTest {

    @AfterEach
    void clear() {
        FlagContextHolder.clear();
    }

    @Test
    void doFilter_populatesHolderFromResolver_duringChain() throws Exception {
        FlagContext resolved = FlagContext.forTenant("tenant-1");
        CapturingFilterChain chain = new CapturingFilterChain();
        FlagContextFilter filter = new FlagContextFilter(() -> resolved);

        filter.doFilter(null, null, chain);

        assertThat(chain.capturedDuringChain).isEqualTo(resolved);
    }

    @Test
    void doFilter_clearsHolder_afterChainReturns() throws Exception {
        FlagContextFilter filter = new FlagContextFilter(() -> FlagContext.forTenant("tenant-1"));

        filter.doFilter(null, null, new CapturingFilterChain());

        assertThat(FlagContextHolder.get()).isNull();
    }

    @Test
    void doFilter_clearsHolder_evenWhenChainThrows() {
        FlagContextFilter filter = new FlagContextFilter(FlagContext::anonymous);

        assertThatThrownBy(() -> filter.doFilter(null, null, new ThrowingFilterChain()))
                .isInstanceOf(ServletException.class);
        assertThat(FlagContextHolder.get()).isNull();
    }

    @Test
    void doFilter_fallsBackToAnonymous_whenResolverThrows() throws Exception {
        CapturingFilterChain chain = new CapturingFilterChain();
        FlagContextFilter filter = new FlagContextFilter(() -> {
            throw new IllegalStateException("resolver bug");
        });

        filter.doFilter(null, null, chain);

        assertThat(chain.capturedDuringChain).isEqualTo(FlagContext.anonymous());
    }

    private static final class CapturingFilterChain implements FilterChain {
        FlagContext capturedDuringChain;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            capturedDuringChain = FlagContext.current();
        }
    }

    private static final class ThrowingFilterChain implements FilterChain {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) throws ServletException {
            throw new ServletException("downstream failure");
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn -pl flags-spring-boot-starter test -Dtest=FlagContextFilterTest`
Expected: FAIL — compilation error, `FlagContextFilter` does not exist.

- [ ] **Step 5: Write the implementation**

```java
package com.acme.flags.springboot;

import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagContextHolder;
import com.acme.flags.api.FlagContextResolver;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlagContextFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(FlagContextFilter.class);

    private final FlagContextResolver resolver;

    public FlagContextFilter(FlagContextResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            FlagContextHolder.set(resolveOrFallback());
            chain.doFilter(request, response);
        } finally {
            FlagContextHolder.clear();
        }
    }

    private FlagContext resolveOrFallback() {
        try {
            return resolver.resolve();
        } catch (RuntimeException e) {
            log.warn("FlagContextResolver failed, falling back to anonymous context", e);
            return FlagContext.anonymous();
        }
    }
}
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn -pl flags-spring-boot-starter test -Dtest=FlagContextFilterTest`
Expected: PASS, 4/4.

- [ ] **Step 7: Commit**

```bash
git add flags-api/src/main/java/com/acme/flags/api/FlagContextResolver.java flags-spring-boot-starter/pom.xml flags-spring-boot-starter/src/main/java/com/acme/flags/springboot/FlagContextFilter.java flags-spring-boot-starter/src/test/java/com/acme/flags/springboot/FlagContextFilterTest.java
git commit -m "Add FlagContextResolver SPI and FlagContextFilter"
```

---

### Task 4: Wire into `FlagsAutoConfiguration`

**Files:**
- Modify: `flags-spring-boot-starter/src/main/java/com/acme/flags/springboot/FlagsAutoConfiguration.java`
- Modify: `flags-spring-boot-starter/src/test/java/com/acme/flags/springboot/FlagsAutoConfigurationTest.java`

**Interfaces:**
- Consumes: `FlagContextResolver` (Task 3), `FlagContextFilter` (Task 3).
- Produces: a default `FlagContextResolver` bean (anonymous, `@ConditionalOnMissingBean`) and a `FilterRegistrationBean<FlagContextFilter>` bean registered only under `@ConditionalOnWebApplication(type = SERVLET)`.

- [ ] **Step 1: Write the failing tests**

Add to `FlagsAutoConfigurationTest.java` (existing file — add these imports and test methods, keep existing tests unchanged):

```java
import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagContextResolver;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
```

```java
    @Test
    void providesDefaultAnonymousFlagContextResolver() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FlagContextResolver.class);
            assertThat(context.getBean(FlagContextResolver.class).resolve())
                    .isEqualTo(FlagContext.anonymous());
        });
    }

    @Test
    void doesNotRegisterFlagContextFilter_inNonWebApplication() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));
    }

    @Test
    void registersFlagContextFilter_inServletWebApplication() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(FlagsAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    Object filter = context.getBean(FilterRegistrationBean.class).getFilter();
                    assertThat(filter).isInstanceOf(FlagContextFilter.class);
                });
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl flags-spring-boot-starter test -Dtest=FlagsAutoConfigurationTest`
Expected: FAIL — no `FlagContextResolver` bean exists yet.

- [ ] **Step 3: Update `FlagsAutoConfiguration`**

Add these imports:

```java
import com.acme.flags.api.FlagContext;
import com.acme.flags.api.FlagContextResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
```

Add these two beans to the class body:

```java
    @Bean
    @ConditionalOnMissingBean(FlagContextResolver.class)
    public FlagContextResolver anonymousFlagContextResolver() {
        return FlagContext::anonymous;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<FlagContextFilter> flagContextFilterRegistration(FlagContextResolver resolver) {
        FilterRegistrationBean<FlagContextFilter> registration =
                new FilterRegistrationBean<>(new FlagContextFilter(resolver));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl flags-spring-boot-starter test -Dtest=FlagsAutoConfigurationTest`
Expected: PASS, all 5 tests (2 pre-existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add flags-spring-boot-starter/src/main/java/com/acme/flags/springboot/FlagsAutoConfiguration.java flags-spring-boot-starter/src/test/java/com/acme/flags/springboot/FlagsAutoConfigurationTest.java
git commit -m "Wire FlagContextResolver default bean and FlagContextFilter into FlagsAutoConfiguration"
```

---

### Task 5: Fix the dead cross-reference in `facade-design.md` §3

**Files:**
- Modify: `docs/feature-flags-facade-design.md`

**Interfaces:** none (documentation only).

- [ ] **Step 1: Replace the stale comment and code sample**

Find this block in §3 (`### \`FlagContext\``):

```java
    public static FlagContext current() {
        // resolves from request-scoped tenant/security context; see §6 for trait sourcing
    }
```

Replace with:

```java
    public static FlagContext current() {
        // resolves from FlagContextHolder (a ThreadLocal), populated by flags-spring-boot-starter's
        // FlagContextFilter from the active FlagContextResolver bean; see the Slice A spec for the
        // full design: docs/superpowers/specs/2026-07-09-flags-v1-slice-a-facade-hardening-design.md
    }
```

- [ ] **Step 2: Commit**

```bash
git add docs/feature-flags-facade-design.md
git commit -m "Fix dead cross-reference in facade-design.md: point FlagContext.current() at the Slice A spec"
```

---

### Task 6: `FlagEngineUnavailableException` + `FlagEngine` javadoc

**Files:**
- Create: `flags-api/src/main/java/com/acme/flags/spi/FlagEngineUnavailableException.java`
- Modify: `flags-api/src/main/java/com/acme/flags/spi/FlagEngine.java`

**Interfaces:**
- Produces: `FlagEngineUnavailableException extends RuntimeException` — consumed by Task 7 (`MockFlagEngine`) and Task 8 (`DefaultFeatureFlags`'s narrowed catch).

- [ ] **Step 1: Create the exception type**

```java
package com.acme.flags.spi;

public final class FlagEngineUnavailableException extends RuntimeException {

    public FlagEngineUnavailableException(String message) {
        super(message);
    }

    public FlagEngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 2: Update `FlagEngine`'s javadoc**

Replace the file's contents:

```java
package com.acme.flags.spi;

import java.util.Map;

/**
 * Contract every flag-evaluation engine must satisfy.
 *
 * <p>MUST return {@code defaultValue} for an unknown/unrecognized key — never throw for "flag
 * doesn't exist." MUST throw {@link FlagEngineUnavailableException} — and only that type — for
 * transport/connectivity failure; any other {@link RuntimeException} an implementation throws is
 * treated by {@code DefaultFeatureFlags} as a bug, not an outage, and propagates uncaught. MUST
 * treat a {@code null} or empty context map as equivalent to "no targeting context available," not
 * as an error. MUST be safe for concurrent calls from multiple threads.
 */
public interface FlagEngine {

    boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue);

    <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue);
}
```

- [ ] **Step 3: Verify the module still compiles**

Run: `mvn -pl flags-api compile`
Expected: BUILD SUCCESS (no behavioral change yet — `DefaultFeatureFlags` still catches broad `RuntimeException`, narrowed in Task 8).

- [ ] **Step 4: Commit**

```bash
git add flags-api/src/main/java/com/acme/flags/spi/FlagEngineUnavailableException.java flags-api/src/main/java/com/acme/flags/spi/FlagEngine.java
git commit -m "Add FlagEngineUnavailableException and document it in the FlagEngine SPI contract"
```

---

### Task 7: Update `MockFlagEngine` to support both exception types

**Files:**
- Modify: `flags-api/src/test/java/com/acme/flags/api/MockFlagEngine.java`

**Interfaces:**
- Consumes: `FlagEngineUnavailableException` (Task 6).
- Produces: `throwOnEvaluate()` (unchanged call sites, now throws `FlagEngineUnavailableException` instead of a plain `RuntimeException`) and a new overload `throwOnEvaluate(RuntimeException)` — consumed by Task 8's new propagation test.

- [ ] **Step 1: Update the implementation**

Replace the file's contents:

```java
package com.acme.flags.api;

import com.acme.flags.spi.FlagEngine;
import com.acme.flags.spi.FlagEngineUnavailableException;
import java.util.Map;

final class MockFlagEngine implements FlagEngine {

    private RuntimeException exceptionToThrow;
    private Boolean fixedBooleanResult;
    private Object fixedVariantResult;

    void throwOnEvaluate() {
        this.exceptionToThrow = new FlagEngineUnavailableException("engine unreachable");
    }

    void throwOnEvaluate(RuntimeException exception) {
        this.exceptionToThrow = exception;
    }

    void returnBoolean(boolean value) {
        this.fixedBooleanResult = value;
    }

    void returnVariant(Object value) {
        this.fixedVariantResult = value;
    }

    @Override
    public boolean evaluateBoolean(String key, Map<String, String> context, boolean defaultValue) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        return fixedBooleanResult != null ? fixedBooleanResult : defaultValue;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T evaluateVariant(String key, Map<String, String> context, Class<T> type, T defaultValue) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        return fixedVariantResult != null ? (T) fixedVariantResult : defaultValue;
    }
}
```

- [ ] **Step 2: Run the existing `DefaultFeatureFlagsTest` suite to confirm no regression**

Run: `mvn -pl flags-api test -Dtest=DefaultFeatureFlagsTest`
Expected: PASS, all 7 pre-existing tests still green — `throwOnEvaluate()`'s call sites are unchanged, only the exception type it throws changed, and `DefaultFeatureFlags` still catches broad `RuntimeException` at this point (narrowed in Task 8), so `FlagEngineUnavailableException` (itself a `RuntimeException`) is still caught the same way.

- [ ] **Step 3: Commit**

```bash
git add flags-api/src/test/java/com/acme/flags/api/MockFlagEngine.java
git commit -m "MockFlagEngine: throw FlagEngineUnavailableException by default, allow overriding the type"
```

---

### Task 8: Narrow `DefaultFeatureFlags`'s catch clauses

**Files:**
- Modify: `flags-api/src/main/java/com/acme/flags/api/DefaultFeatureFlags.java`
- Modify: `flags-api/src/test/java/com/acme/flags/api/DefaultFeatureFlagsTest.java`

**Interfaces:**
- Consumes: `FlagEngineUnavailableException` (Task 6), `MockFlagEngine.throwOnEvaluate(RuntimeException)` (Task 7).

- [ ] **Step 1: Write the failing test**

Add to `DefaultFeatureFlagsTest.java` (existing file — add this import and test method):

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

```java
    @Test
    void isEnabled_propagatesException_whenEngineThrowsNonTransportError() {
        engine.throwOnEvaluate(new IllegalStateException("adapter bug"));

        assertThatThrownBy(() -> flags.isEnabled(BOOL_FLAG, FlagContext.anonymous()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("adapter bug");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl flags-api test -Dtest=DefaultFeatureFlagsTest#isEnabled_propagatesException_whenEngineThrowsNonTransportError`
Expected: FAIL — the current broad `catch (RuntimeException e)` swallows the `IllegalStateException` and returns the default value instead of letting it propagate.

- [ ] **Step 3: Narrow the catch clauses**

In `DefaultFeatureFlags.java`, add the import:

```java
import com.acme.flags.spi.FlagEngineUnavailableException;
```

Change both occurrences of `catch (RuntimeException e)` to:

```java
        } catch (FlagEngineUnavailableException e) {
```

(Two occurrences: in `isEnabled` and in `getVariant`. The `log.warn` message and everything else in each catch block stays unchanged.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl flags-api test -Dtest=DefaultFeatureFlagsTest`
Expected: PASS, all 8 tests (7 pre-existing + 1 new) — the pre-existing fallback tests still pass because `MockFlagEngine.throwOnEvaluate()` now throws `FlagEngineUnavailableException` specifically (Task 7), which the narrowed catch still catches.

- [ ] **Step 5: Commit**

```bash
git add flags-api/src/main/java/com/acme/flags/api/DefaultFeatureFlags.java flags-api/src/test/java/com/acme/flags/api/DefaultFeatureFlagsTest.java
git commit -m "Narrow DefaultFeatureFlags's catch to FlagEngineUnavailableException; other RuntimeExceptions now propagate"
```

---

### Task 9: Update `GoffFlagEngine`'s javadoc

**Files:**
- Modify: `flags-engine-goff/src/main/java/com/acme/flags/engine/goff/GoffFlagEngine.java`

**Interfaces:** none (documentation only — no behavioral change to the skeleton's `UnsupportedOperationException` throws).

- [ ] **Step 1: Replace the outdated claim**

Find this paragraph in the class javadoc:

```java
 * <p><strong>Status: pre-ratification skeleton.</strong> GOFF is the <em>recommended</em> engine in
 * ADR 0002 but is not yet ratified, and ADR 0002 blocks implementation start until it is. This class
 * defines the adapter's shape without pulling in the OpenFeature/GOFF dependency or implementing
 * evaluation. Both methods throw {@link UnsupportedOperationException}; behind
 * {@code DefaultFeatureFlags} — which wraps engine calls in try/catch and falls back to the flag's
 * default — that surfaces as safe, default-valued evaluation rather than a crash.
```

Replace with:

```java
 * <p><strong>Status: pre-ratification skeleton.</strong> GOFF is the <em>recommended</em> engine in
 * ADR 0002 but is not yet ratified, and ADR 0002 blocks implementation start until it is. This class
 * defines the adapter's shape without pulling in the OpenFeature/GOFF dependency or implementing
 * evaluation. Both methods throw {@link UnsupportedOperationException}, which — unlike
 * {@link com.acme.flags.spi.FlagEngineUnavailableException} — is <strong>not</strong> caught by
 * {@code DefaultFeatureFlags}'s narrowed fallback handling, so calling this skeleton live would
 * propagate a real exception rather than silently falling back. Nothing wires it live today:
 * {@code FlagsAutoConfiguration} has no {@code goff} branch yet.
```

- [ ] **Step 2: Verify the module still compiles**

Run: `mvn -pl flags-engine-goff compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add flags-engine-goff/src/main/java/com/acme/flags/engine/goff/GoffFlagEngine.java
git commit -m "Update GoffFlagEngine javadoc: narrowed catch no longer absorbs its UnsupportedOperationException"
```

---

### Task 10: Full-reactor verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full reactor build**

Run: `mvn -B verify`
Expected: BUILD SUCCESS across all 7 modules — `flags-api` (including `VendorFreedomArchTest`), `flags-noop`, `flags-test-fixtures`, `flags-contract-test`, `flags-spring-boot-starter`, `flags-engine-goff`, `flags-bom`.

- [ ] **Step 2: Confirm `VendorFreedomArchTest` specifically still passes**

Run: `mvn -pl flags-api test -Dtest=VendorFreedomArchTest`
Expected: PASS — `FlagContextHolder`, `FlagContextResolver`, and `FlagEngineUnavailableException` all live in `com.acme.flags.api`/`com.acme.flags.spi` and only import `java..`; `FlagContextFilter` (the one class that imports `jakarta.servlet.*`) lives in `flags-spring-boot-starter`, a module this rule doesn't scan.

- [ ] **Step 3: No commit needed** — this task is verification only, confirming Tasks 1-9's commits are individually and collectively green.
