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

Paths are resolved relative to the reactor root (where you invoke `mvn`), not the module directory.

## Scope

Tier 1a — single-service detection, proven against the synthetic fixtures under
`src/test/resources/fixtures/`. Cross-repo aggregation (Tier 1b) is out of scope until real
consuming services with their own registries exist. See
`../docs/superpowers/specs/2026-07-09-flags-v1-slice-e-interaction-scan-design.md`.
