# Rule coverage: Compiler, Detekt, and Lint

This document is retired. It used to maintain an independent per-rule coverage
matrix across the Compiler plugin, Detekt, and Android Lint, but that matrix
regularly drifted out of sync with the real rule registries.

Rule coverage and suppression IDs now live in two actively maintained sources
instead of a fourth, duplicated one:

- **Rule codes, descriptions, and per-practice guidance**:
  [BEST_PRACTICES_COROUTINES.md](BEST_PRACTICES_COROUTINES.md)
- **Suppression IDs per tool (Compiler, Detekt, Lint, IntelliJ)**:
  [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md)
- **Canonical machine-readable list of codes, severities, and per-tool
  coverage**: [rule-codes.yml](rule-codes.yml)

If you were looking for which tool implements a given rule code, or its
suppression identifier, start with `rule-codes.yml` and cross-reference
`SUPPRESSING_RULES.md`.
