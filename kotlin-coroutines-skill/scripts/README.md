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

# Eval runner

Runs `kotlin-coroutines-skill/evals/evals.json` against a live agent client, once with the
skill enabled and once with it disabled, per the with/without-skill comparison in
[CONTRIBUTING.md](https://github.com/Kotlin/kotlin-agent-skills/blob/main/CONTRIBUTING.md#testing).

## Usage

```bash
# Smoke test: 1 case, both conditions
python3 kotlin-coroutines-skill/scripts/run_evals.py --client claude --limit 1

# Full 29-case run
python3 kotlin-coroutines-skill/scripts/run_evals.py --client claude --model sonnet

# Re-run just a few ids (e.g. after tweaking the skill)
python3 kotlin-coroutines-skill/scripts/run_evals.py --client claude --ids 1,5,28
```

Output lands in `../kotlin-coroutines-skill-workspace/iteration-N/eval-<slug>/{with_skill,without_skill}/`
(`response.md`, `raw.json`, `timing.json` per run) — one directory above the repo root, so it never
pollutes the git tree. Already-completed case/condition pairs are skipped on re-run, so an
interrupted batch can be resumed by re-running the same command.

Grading each response against its `assertions` (PASS/FAIL + evidence) is a separate, later step —
this script only runs and captures raw output.

## Client support

- **`--client claude`** — verified against the local Claude Code CLI. Loads the skill via this
  repo's own plugin registration (`.claude-plugin/plugin.json`, enabled globally as
  `kotlin-coroutines-skill@structured-coroutines`); the without_skill baseline disables just that
  plugin for one invocation via `--settings`, run from a directory outside the repo so the model
  can't stumble onto the skill's own reference files through its file-reading tools.
- **`--client codex`** — **unverified**. The `codex` CLI was not installed on the machine this
  script was written on, so the exact flags for JSON output and for disabling a
  project-registered skill could not be confirmed. Check `codex exec --help` and fix
  `run_codex()` in `run_evals.py` before trusting numbers from this path.
