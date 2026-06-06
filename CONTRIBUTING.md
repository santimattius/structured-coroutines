# Contributing to Structured Coroutines

Thank you for your interest in contributing. This project enforces structured
concurrency in Kotlin through a compiler plugin, Detekt rules, Android Lint,
IntelliJ inspections, and supporting tooling.

## Code of Conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By
participating, you agree to uphold it.

## How to Contribute

1. **Check existing issues** — Search [open issues](https://github.com/santimattius/structured-coroutines/issues) before opening a new one.
2. **Open an issue first for larger changes** — New rules, breaking changes, or cross-module refactors benefit from discussion before implementation.
3. **Fork and branch** — Create a feature branch from `main`.
4. **Make focused changes** — Keep pull requests scoped to one concern when possible.
5. **Run tests locally** — CI must pass before merge.
6. **Open a pull request** — Fill in the PR template and link related issues.

## Development Setup

### Requirements

- JDK 21
- Kotlin 2.3+
- Gradle 8.0+ (wrapper included)

### Build and test

```bash
git clone https://github.com/santimattius/structured-coroutines.git
cd structured-coroutines

# Publish artifacts to local Maven for integration testing
./gradlew publishToMavenLocal

# Run all module tests
./gradlew test

# Core modules only (matches CI)
./gradlew :compiler:test :detekt-rules:test :lint-rules:test

# IntelliJ plugin compilation (matches CI)
./gradlew :intellij-plugin:compileKotlin :intellij-plugin:instrumentCode

# Detekt sample validation
./gradlew :sample-detekt:detekt
```

## Project Structure

| Module | Purpose |
|--------|---------|
| `compiler/` | K2/FIR compiler plugin |
| `detekt-rules/` | Detekt static analysis rules |
| `lint-rules/` | Android Lint detectors |
| `intellij-plugin/` | IntelliJ/Android Studio inspections and quick fixes |
| `gradle-plugin/` | Gradle integration and profiles |
| `annotations/` | `@StructuredScope` and related annotations |
| `sample/` | Compiler rule compilation examples |
| `sample-detekt/` | Detekt rule validation samples |
| `kotlin-coroutines-skill/` | AI/agent skill and reference docs |

## Adding or Changing Rules

New rules should stay aligned with [Best Practices](docs/BEST_PRACTICES_COROUTINES.md)
and the shared manifest in [`docs/rule-codes.yml`](docs/rule-codes.yml).

When adding a rule, update as applicable:

1. **Rule implementation** — Compiler checker, Detekt rule, Lint detector, and/or IntelliJ inspection.
2. **`docs/rule-codes.yml`** — Rule code, suppression IDs, and doc anchor.
3. **`docs/SUPPRESSING_RULES.md`** — Suppression identifiers across layers.
4. **Sample code** — Example in `sample/` and/or `sample-detekt/`.
5. **Tests** — Unit tests for the rule logic.
6. **Reference docs** — Entry under `kotlin-coroutines-skill/references/` when the rule maps to a best practice.
7. **CHANGELOG.md** — User-facing summary of the change.

Rule codes follow the pattern `CATEGORY_NNN` (e.g. `SCOPE_001`, `FLOW_012`).

## Coding Guidelines

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
- Match existing patterns in the module you are editing.
- Keep user-facing messages in `.properties` bundles for i18n (see README i18n section).
- Prefer structured concurrency in any new coroutine code (no `GlobalScope`, no `runBlocking` in suspend functions).

## Pull Request Checklist

- [ ] Tests pass locally (`./gradlew test` or the relevant module tasks).
- [ ] New/changed rules include tests and sample code where applicable.
- [ ] Documentation updated (`rule-codes.yml`, module README, CHANGELOG as needed).
- [ ] Commit messages describe the **why**, not only the **what**.

## Reporting Security Issues

Please do **not** open public issues for security vulnerabilities. See
[SECURITY.md](SECURITY.md) for responsible disclosure.

## Questions

For usage questions, open a [GitHub Discussion](https://github.com/santimattius/structured-coroutines/discussions)
(if enabled) or an issue with the **question** label.

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).
