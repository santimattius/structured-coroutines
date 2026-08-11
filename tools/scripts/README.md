Everything under `tools/` is dev/build/eval tooling for `kotlin-coroutines-skill/` — none of it is
loaded by the Agent Skills runtime (only `kotlin-coroutines-skill/SKILL.md` and
`kotlin-coroutines-skill/references/*.md` are). Kept separate so the skill's own folder only ever
contains what actually ships/loads.

# Skill reference generator

Regenerates **self-contained** `references/ref-*.md` files for iter-2/3 rules listed in `ref-manifest.yml`.

## Sources

| File | Role |
|------|------|
| `docs/BEST_PRACTICES_COROUTINES.md` | Bad Practice / Recommended prose; kotlin blocks when present |
| `docs/rule-codes.yml` | Toolkit section (rule code, tool IDs, severity) |
| `tools/ref-manifest.yml` | Which ref files to generate |
| `tools/ref-examples.yml` | Kotlin BAD/GOOD examples, Why overrides, Quick fix rows |

## Usage

From the repository root:

```bash
# Regenerate 27 manifest-listed refs
python3 tools/scripts/generate_refs.py

# CI: fail if refs are stale (no writes)
python3 tools/scripts/generate_refs.py --check
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

# Eval pipeline: install → run → grade

Three scripts, run in order, that download the skill the way a real consumer would, install it
into a real Kotlin project, run `tools/evals/evals.json` against it with/without
the skill enabled, and grade the results into pass-rate/cost/duration metrics — per the
with/without-skill comparison in
[CONTRIBUTING.md](https://github.com/Kotlin/kotlin-agent-skills/blob/main/CONTRIBUTING.md#testing).

```bash
# 1. Download + install: adds this repo as a marketplace (`claude plugin marketplace add`) and
#    installs the plugin scoped to a fresh copy of sample/ (`claude plugin install ... -s local`).
#    Writes <workspace>/install-info.json, consumed by the next two scripts.
python3 tools/scripts/install_skill.py

# 2. Run: each case's prompt runs twice from inside the installed project -- once with the skill
#    enabled, once with it disabled via a --settings override for that one call.
python3 tools/scripts/run_evals.py --client claude --model sonnet

# 3. Grade: an independent judge call (fresh session, skill disabled, default model haiku) scores
#    each response against its assertions and writes metrics.json / metrics.md.
python3 tools/scripts/grade_evals.py
```

Smoke test the whole pipeline cheaply first:

```bash
python3 tools/scripts/install_skill.py --workspace /tmp/skill-eval-smoke
python3 tools/scripts/run_evals.py --workspace /tmp/skill-eval-smoke --ids 1 --model haiku
python3 tools/scripts/grade_evals.py --workspace /tmp/skill-eval-smoke --ids 1
```

Re-run any script with `--ids 1,5,28` to redo specific cases (e.g. after tweaking the skill) —
already-completed case/condition pairs (and already-graded cases) are skipped, so an interrupted
batch resumes with the same command.

Output lands in `../kotlin-coroutines-skill-workspace/` by default (one directory above the repo
root, so it never pollutes the git tree):

```
install-info.json                                          # plugin id + installed project path
iteration-N/
  eval-<slug>/{with_skill,without_skill}/outputs/response.md
  eval-<slug>/{with_skill,without_skill}/{timing,raw}.json
  eval-<slug>/grade.json                                    # per-assertion verdicts
  metrics.json                                               # full aggregate detail
  metrics.md                                                 # human-readable summary table
```

## Verified mechanics (Claude Code CLI 2.1.220)

- `claude plugin marketplace add <path-or-github-url>` and `claude plugin install <id> -s local`
  are both idempotent (safe to re-run) and, with `-s local`, write only to
  `<target-project>/.claude/settings.local.json` — never to this repo's tracked files or to
  `~/.claude/settings.json`.
- A fully isolated `CLAUDE_CONFIG_DIR` was tried and rejected: it breaks `-p` auth, since OAuth/
  keychain credentials are only ever read from the real user config. The without_skill baseline
  instead reuses the `--settings enabledPlugins:false` override (unaffected by this), which was
  already verified in `run_evals.py`.
- `claude plugin eval` — a native, built-in with/without ablation + LLM-judge grader — exists in
  this CLI version but reports `early access` and refused to run (`claude plugin eval init --bare`
  produced no output beyond that message). If a future CLI version unlocks it, it likely obsoletes
  most of `run_evals.py` + `grade_evals.py`; re-check `claude plugin eval --help` before adding
  more custom grading logic here.

## Client support

- **`--client claude`** (default, `run_evals.py` only) — verified against the local Claude Code
  CLI, as above.
- **`--client codex`** — **unverified**. The `codex` CLI was not installed on the machine this
  script was written on, so the exact flags for JSON output and for disabling a
  project-registered skill could not be confirmed. Check `codex exec --help` and fix
  `run_codex()` in `run_evals.py` before trusting numbers from this path.

# Manifest sync guard

Asserts `.claude-plugin/plugin.json`, its matching `.claude-plugin/marketplace.json` `plugins[]`
entry, and `.codex-plugin/plugin.json` (when present) agree on `name`, `description`, `author`,
`license`, and `keywords`. An assertion script, not a generator — three hand-edited JSON files
justify a check, not a build step.

```bash
python3 tools/scripts/check_manifest_sync.py
```

Wired into CI via `.github/workflows/validate-manifests.yml` (own workflow, separate from
`validate-skill.yml`), triggered on changes under `.claude-plugin/**`, `.codex-plugin/**`, or the
script itself.

**Excluded from comparison, on purpose:** `version` / `metadata.version`. Manifest `version`
(package release, currently `1.1.0`) and `kotlin-coroutines-skill/SKILL.md`'s
`metadata.version` (skill content revision, currently `3.0.0`) are independent semantics —
comparing them would produce permanent false positives. `marketplace.json`'s top-level
`version` (catalog version) is excluded for the same reason. `repository` is present in both
plugin manifests but is out of scope for this guard's compared-field set.

**Absent-manifest behavior:** if `.codex-plugin/plugin.json` doesn't exist yet, the script exits
`0` with a printed skip notice instead of failing — Codex packaging (Tier 2) can slip or be
rolled back independently of this guard without reddening CI. Mirrors this repo's existing
`--client codex` **unverified** convention above: Codex support here degrades gracefully rather
than hard-failing when unconfirmed/not-yet-shipped.
