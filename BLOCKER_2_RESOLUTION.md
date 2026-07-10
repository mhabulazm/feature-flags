# Blocker 2 Resolution: InMemoryFlagEngine + Parameter Store Caching

## Status: ✅ Path A Complete

**Date:** 2025-01-XX
**Resolution:** Path A (structural argument + unit test) implemented
**Path B (real AWS integration test):** Deferred until after ADR 0003 is accepted

---

## What Was Done

### 1. Added Unit Test: `InMemoryFlagEngineNoHotPathIoTest`

**Location:** `flags-noop/src/test/java/com/acme/flags/noop/InMemoryFlagEngineNoHotPathIoTest.java`

**Purpose:** Confirms that `InMemoryFlagEngine` does not perform per-evaluation I/O by:
- Spying on `FlagOverridesProperties` with Mockito
- Calling `evaluateBoolean()` and `evaluateVariant()` multiple times
- Verifying that only `getOverrides()` is called (map lookup), no refresh/rebind occurs

**Test Coverage:**
- ✅ Multiple evaluations of same flag (10 iterations)
- ✅ Evaluations with no override (returns default)
- ✅ String variant evaluations
- ✅ Multiple different flags (3 flags × 5 iterations)
- ✅ Verification that no other Spring binding methods are called

### 2. Added Warning Comment to `InMemoryFlagEngine`

**Location:** `flags-noop/src/main/java/com/acme/flags/noop/InMemoryFlagEngine.java`

**Added Class-level Javadoc:**
```java
/**
 * <strong>WARNING:</strong> Do NOT add {@code @RefreshScope} to {@link FlagOverridesProperties}.
 * That would introduce periodic I/O and violate the "no I/O on hot path" contract from
 * ADR 0002 B3. If live reload of overrides is needed, use a different mechanism that does not
 * rebind on the evaluation path.
 */
```

### 3. Added Mockito Dependency

**Location:** `flags-noop/pom.xml`

Added `mockito-core` to test dependencies to enable spying on `FlagOverridesProperties`.

---

## Structural Argument (Documented in Test Javadoc)

1. **Spring's `@ConfigurationProperties` behavior:** Binding happens once at `ApplicationContext` startup
2. **After binding:** `FlagOverridesProperties.getOverrides()` returns an in-memory `Map`
3. **Evaluation path:** `InMemoryFlagEngine.evaluateBoolean()` calls `properties.getOverrides().get(key)` — a plain `Map.get()` lookup
4. **No I/O:** Whether values came from properties file, environment variables, or Parameter Store — the binding happens once, not per-evaluation

---

## Path B: Deferred Implementation Plan

**Trigger:** After ADR 0003 is accepted

**What to implement:**
1. Add `spring-cloud-aws` dependency to `flags-noop` test scope
2. Use LocalStack (Testcontainers module) to simulate Parameter Store
3. Configure `@ConfigurationProperties` to bind from LocalStack Parameter Store
4. Write integration test that:
   - Binds properties at startup
   - Changes a value in Parameter Store mid-test
   - Calls `evaluateBoolean()` multiple times
   - Asserts the old value is still returned (proves no rebind)

**Estimated Effort:** 4-6 hours

**Files to Create:**
- `flags-noop/src/test/java/com/acme/flags/noop/InMemoryFlagEngineParameterStoreIntegrationTest.java`

**Files to Modify:**
- `flags-noop/pom.xml` (add spring-cloud-aws, testcontainers-localstack)

---

## Checklist Status

| Item | Status |
|------|--------|
| Unit test proving no per-evaluation I/O | ✅ Complete |
| Warning comment about `@RefreshScope` risk | ✅ Complete |
| Structural argument documented | ✅ Complete |
| Path B (real AWS test) | ⏸️ Deferred until ADR 0003 accepted |

---

## Remaining Blockers for ADR 0002

| # | Blocker | Status |
|---|---------|--------|
| 1 | Gate 1 — Peak-load evidence | ⏸️ Blocked |
| 2 | InMemoryFlagEngine + Parameter Store caching | ✅ **Complete** (Path A) |
| 3 | Gate 2 — Self-serve evidence | ⏸️ Blocked |
| 4 | ADR 0003 scope confirmation | ⏸️ Blocked |
| 5 | Facade ownership | ⏸️ Blocked (deferred) |

---

## Next Steps

**Ready to tackle:**
- Gate 1 — Peak-load evidence (build load test harness)
- Gate 2 — Self-serve evidence (usability test)
- ADR 0003 review (Tier 1/Tier 2 scope confirmation)

Which one next?