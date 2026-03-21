# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Structured Coroutines** is a multi-layered Kotlin toolkit that enforces structured concurrency best practices. It consists of:

- **`:annotations`** — `@StructuredScope` annotation (Kotlin Multiplatform)
- **`:compiler`** — K2/FIR Kotlin Compiler Plugin (12 rules, compile-time checks)
- **`:gradle-plugin`** — Gradle integration and configuration DSL for the compiler plugin
- **`:detekt-rules`** — Detekt static analysis rules (18 rules)
- **`:lint-rules`** — Android Lint rules (21 rules, includes quick fixes)
- **`:intellij-plugin`** — IntelliJ/Android Studio IDE plugin (13 inspections, 12 quick fixes, 6 intentions, gutter icons, tool window)
- **`:kotlin-coroutines-skill`** — AI/agent guidance system with reference docs and system prompt
- **`:sample`** — Intentional violations used to validate the compiler plugin (expected to fail compilation)
- **`:sample-detekt`** — Intentional violations for validating Detekt rules

## Commands

### Build & Publish

```bash
./gradlew publishToMavenLocal       # Publish all modules locally (needed to test against local builds)
./gradlew publishToMavenCentral     # Publish to Maven Central
```

### Testing

```bash
./gradlew testAll                   # Run tests for all modules (excludes :sample, which fails by design)
./gradlew :compiler:test            # Compiler plugin tests (Gradle TestKit functional tests)
./gradlew :detekt-rules:test        # Detekt rule tests
./gradlew :lint-rules:test          # Android Lint tests
./gradlew :intellij-plugin:test     # IDE plugin tests
./gradlew :gradle-plugin:test       # Gradle plugin tests

# Run a single test class by name pattern
./gradlew :compiler:test --tests "*GlobalScope*"
./gradlew :detekt-rules:test --tests "*BlockingCall*"
```

### Validation

```bash
./gradlew validateSample            # Compiles :sample and verifies expected errors appear
./gradlew :sample-detekt:detekt     # Validates Detekt rules against intentional violations
```

### IDE Plugin

```bash
./gradlew :intellij-plugin:buildPlugin   # Build plugin ZIP (output: intellij-plugin/build/distributions/)
./gradlew :intellij-plugin:runIde        # Launch IDE sandbox with the plugin installed
```

## Architecture

### Layered Enforcement

Each layer is independent but targets the same set of anti-patterns:

1. **Compile-time** (`:compiler` + `:gradle-plugin`): Fastest feedback; K2/FIR checkers block or warn during compilation. Severity per rule is configurable via the `structuredCoroutines {}` Gradle extension.
2. **Static analysis** (`:detekt-rules`, `:lint-rules`): Run in CI; detekt rules overlap with the compiler plugin but also include 8 detekt-only rules. Lint rules add 4 Android-specific rules and automated quick fixes.
3. **IDE** (`:intellij-plugin`): Real-time feedback; mirrors compiler and lint rules as inspections with quick fixes and refactoring intentions.
4. **AI Guidance** (`:kotlin-coroutines-skill`): 19 reference docs and a system prompt for AI agents to give consistent, correct coroutine advice.

### Compiler Plugin Internals (`:compiler`)

- Entry point: `StructuredCoroutinesCompilerPluginRegistrar.kt`
- FIR extensions registered in `ScoroutinesFirExtensionRegistrar.kt`
- All checkers are aggregated in `ScoroutinesCallCheckerExtension.kt`
- Severity levels per rule defined in `PluginConfiguration.kt`; error codes in `StructuredCoroutinesErrors.kt`
- Tests use Gradle TestKit (functional tests that compile real Kotlin source files and assert diagnostics)

### Rule Sync

Rules are deliberately coordinated across all three analysis layers. The canonical mapping is in `docs/RULES_SYNC_COMPARISON.md` and the rule code registry is in `docs/rule-codes.yml`. When adding or modifying a rule, update it in all applicable layers and keep the suppression IDs consistent (see `docs/SUPPRESSING_RULES.md`).

### Sample Projects

- `:sample` contains **intentionally invalid code** that triggers compiler plugin errors. `./gradlew validateSample` compiles it and asserts the expected errors appear. Do not fix violations in `:sample`.
- `:sample-detekt` contains **intentionally invalid code** for Detekt rule validation. Running `./gradlew :sample-detekt:detekt` is how you confirm Detekt rules fire correctly.

### Locale / i18n

Compiler error messages support English and Spanish. The locale is set via JVM args in `gradle.properties`:

```properties
org.gradle.jvmargs=-Dstructured.coroutines.compiler.locale=en
kotlin.daemon.jvmargs=-Dstructured.coroutines.compiler.locale=en
# Options: en, es, default (JVM default locale)
```

Message properties files live inside the `:compiler` module.

## Key Documentation

- `docs/BEST_PRACTICES_COROUTINES.md` — Canonical coroutine best practices guide (~55 KB)
- `docs/RULES_SYNC_COMPARISON.md` — Maps each rule across compiler / detekt / lint / IDE layers
- `docs/SUPPRESSING_RULES.md` — Unified suppression IDs usable across all tools
- `docs/GRADUAL_ADOPTION.md` — Migration and incremental adoption strategy
- `docs/rule-codes.yml` — Machine-readable rule code registry
