# Skill reference generator

Regenerates **self-contained** `references/ref-*.md` files for iter-2/3 rules listed in `ref-manifest.yml`.

## Sources

| File | Role |
|------|------|
| `docs/BEST_PRACTICES_COROUTINES.md` | Bad Practice / Recommended prose; kotlin blocks when present |
| `docs/rule-codes.yml` | Toolkit section (rule code, tool IDs, severity) |
| `kotlin-coroutines-skill/ref-manifest.yml` | Which ref files to generate |
| `kotlin-coroutines-skill/ref-examples.yml` | Kotlin BAD/GOOD examples, Why overrides, Quick fix rows |

## Usage

From the repository root:

```bash
# Regenerate 27 manifest-listed refs
python3 kotlin-coroutines-skill/scripts/generate_refs.py

# CI: fail if refs are stale (no writes)
python3 kotlin-coroutines-skill/scripts/generate_refs.py --check
```

Gradle wrappers:

```bash
./gradlew generateSkillRefs
./gradlew checkSkillRefs
```

## Dependencies

- Python 3.9+
- **PyYAML** (`pip install pyyaml`) **or** Ruby with psych (default on macOS)

## Adding a new generated ref

1. Add the rule to `docs/BEST_PRACTICES_COROUTINES.md` and `docs/rule-codes.yml`.
2. Add `file` + `rule` to `ref-manifest.yml`.
3. Add `bad` / `good` / `why` to `ref-examples.yml` if BEST_PRACTICES has no kotlin example.
4. Run `generate_refs.py` and commit the output.

Hand-maintained refs (e.g. `ref-1-1-global-scope.md`) are **not** in the manifest and are edited manually.
