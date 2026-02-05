# Kotlin Coroutines Agent Skill

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

Expert guidance for any AI coding tool that supports Agent Skills or custom instructions — **safe structured concurrency**, performance, and Kotlin 1.9/2.0+ best practices for Coroutines.

This package is part of the [Structured Coroutines](https://github.com/santimattius/structured-coroutines) project and was added to provide consistent, rule-based AI/agent-driven guidance for reviewing or refactoring Kotlin/Android coroutine code.

Inspired by the [Swift Concurrency Agent Skill](https://github.com/AvdLee/Swift-Concurrency-Agent-Skill) model: industry-standard practices, no forced architecture. This skill helps agents give **consistent, correct** advice on Kotlin Coroutines without pushing a specific app structure.

---

## Why This Skill Exists

- **Structured Concurrency is easy to get wrong:** `GlobalScope`, wrong Dispatchers, swallowed `CancellationException`, and misuse of `SupervisorJob` lead to leaks, ANRs, and flaky behavior. Many AI answers repeat these mistakes.
- **One source of truth:** This skill encodes a single set of rules (scopes, dispatchers, exceptions, cancellation, testing) so that ChatGPT, Claude, Cursor, or other agents give **aligned** recommendations.
- **Faster reviews and migrations:** Teams adopting coroutines or moving to strict structured concurrency can point their AI at this skill and get code that follows the same checklist (no GlobalScope, proper scopes, `withContext(IO)`, virtual-time tests, etc.).

---

## What’s Included

| Asset | Description |
|-------|-------------|
| **SYSTEM_PROMPT.md** | Full system prompt: identity, strict rules, tone, and required output format (analysis → erroneous code → optimized code → explanation). |
| **SKILL.md** | Playbook (triage): maps topic/error to the right reference file so the agent can jump to the relevant practice. |
| **references/** | One markdown file per best practice (see table below). Each has Bad / Recommended / Why / Quick fix. |
| **CONFIG.json** | Metadata for the skill: name, description, version, triggers, and reference index. |
| **EXAMPLES_SUITE.kt** | Three Kotlin examples with intentional anti-patterns (scope leaks, exception handling, Dispatchers) for testing the agent. |
| **.claude-plugin/** | Claude Code plugin manifest for use with Claude Code (`/plugin` or `--plugin-dir`). |
| **skills/kotlin-coroutines/SKILL.md** | Claude Code Agent Skill entry point (playbook + rules + reference paths). |
| **BEST_PRACTICES_COROUTINES.md** | Full guide lives in the parent repo (`docs/BEST_PRACTICES_COROUTINES.md`); this skill is derived from it. |

### References (per practice)

Each practice from the best-practices doc has a dedicated reference file in **references/**:

| # | Topic | File |
|---|--------|------|
| 1.1 | GlobalScope in production | `references/ref-1-1-global-scope.md` |
| 1.2 | async without await | `references/ref-1-2-async-without-await.md` |
| 1.3 | Breaking structured concurrency | `references/ref-1-3-breaking-structured-concurrency.md` |
| 2.1 | launch on last line of coroutineScope | `references/ref-2-1-launch-last-line-coroutine-scope.md` |
| 2.2 | runBlocking inside suspend | `references/ref-2-2-runblocking-in-suspend.md` |
| 3.1 | Blocking code with wrong Dispatchers | `references/ref-3-1-blocking-wrong-dispatchers.md` |
| 3.2 | Dispatchers.Unconfined | `references/ref-3-2-dispatchers-unconfined.md` |
| 3.3 | Job()/SupervisorJob() as builder context | `references/ref-3-3-job-context-builders.md` |
| 4.1 | Cancellation in intensive loops | `references/ref-4-1-cancellation-intensive-loops.md` |
| 4.2 | Swallowing CancellationException | `references/ref-4-2-swallowing-cancellation-exception.md` |
| 4.3 | Suspend cleanup without NonCancellable | `references/ref-4-3-suspend-cleanup-noncancellable.md` |
| 4.4 | Reusing cancelled scope | `references/ref-4-4-reusing-cancelled-scope.md` |
| 5.1 | SupervisorJob in single builder | `references/ref-5-1-supervisor-job-single-builder.md` |
| 5.2 | CancellationException for domain errors | `references/ref-5-2-cancellation-exception-domain-errors.md` |
| 6.1 | Slow tests with real delays | `references/ref-6-1-slow-tests-real-delays.md` |
| 6.2 | Uncontrolled fire-and-forget in tests | `references/ref-6-2-uncontrolled-fire-and-forget-tests.md` |
| 7.1 | Channel not closed | `references/ref-7-1-channel-close.md` |
| 7.2 | consumeEach with multiple consumers | `references/ref-7-2-consume-each-multiple-consumers.md` |
| 8 | Architecture patterns | `references/ref-8-architecture-patterns.md` |

The agent should use **SKILL.md** (playbook) to choose which reference(s) apply, then apply the strict rules from **SYSTEM_PROMPT.md** and the bad/recommended/quick-fix from the reference(s).

---

## Installation

### Option A: ChatGPT (Custom GPTs)

1. Create a new **Custom GPT** (ChatGPT Plus or Team).
2. In **Configure** → **Instructions**, paste the full content of **SYSTEM_PROMPT.md** (from this repo).
3. Optionally add in **Instructions** or **Knowledge**:  
   *“When the user asks about Kotlin Coroutines, structured concurrency, GlobalScope, Dispatchers, or cancellation, follow the Kotlin Coroutines Agent system prompt and answer in the required format (analysis, erroneous code, optimized code, explanation).”*
4. Save and name the GPT (e.g. “Kotlin Coroutines Expert”).

**Quick test:** Ask: *“Why shouldn’t I use GlobalScope.launch in an Android ViewModel?”* — You should get analysis, bad snippet, good snippet, and explanation.

---

### Option B: Claude (Projects)

1. In Claude, open **Projects** and create or select a project.
2. Go to **Project settings** → **Custom instructions** (or the instructions field for that project).
3. Paste the full content of **SYSTEM_PROMPT.md**.
4. Add a short line: *“For Kotlin Coroutines questions, always use the structured output format: 1) Analysis, 2) Erroneous code, 3) Optimized code, 4) Technical explanation.”*
5. Save.

Use this project when working on Kotlin/Android codebases so Claude consistently applies the same rules.

---

### Option C: Cursor (Rules for AI)

1. In your repo or user config, open or create the **Cursor rules** directory (e.g. `.cursor/rules/` or the path your Cursor version uses for “Rules for AI”).
2. Create a rule file, e.g. **kotlin-coroutines-skill.mdc** or **kotlin-coroutines-skill.md**.
3. Paste the full content of **SYSTEM_PROMPT.md** into that file.
4. If your setup supports it, add a **globs** or **when** condition so the rule applies to Kotlin files, e.g. `**/*.kt`, or to a specific module (e.g. `**/app/**/*.kt`).
5. Reload Cursor / rules so the new rule is active.

**Verification:** Open a `.kt` file with `GlobalScope.launch { }` and ask Cursor to refactor it to follow structured concurrency; the answer should match the skill’s format and rules.

---

### Option D: Claude Code (Plugin)

Claude Code can load this folder as a **plugin** so the Kotlin Coroutines skill (playbook + references) is available as an Agent Skill.

1. **Prerequisites:** [Claude Code](https://code.claude.com/docs) installed and authenticated; version **1.0.33 or later** (run `claude --version`).
2. **Install from a local directory (e.g. this repo):**
   ```bash
   claude --plugin-dir /path/to/structured-coroutines/kotlin-coroutines-skill
   ```
   Replace `/path/to/structured-coroutines/kotlin-coroutines-skill` with the actual path to this `kotlin-coroutines-skill` folder.
3. After Claude Code starts, the skill **kotlin-coroutines** is loaded. Use it when working on Kotlin/Android code or when you ask about coroutines, GlobalScope, Dispatchers, cancellation, testing, or channels.
4. **Install from a marketplace (if you publish this plugin):**  
   See [Discover and install plugins](https://code.claude.com/docs/en/discover-plugins). You can add a marketplace that points at this repo and then run something like:
   ```bash
   /plugin marketplace add owner/repo
   /plugin install kotlin-coroutines-skill
   ```
5. **Verify:** Ask Claude Code to “Review this coroutine code for best practices” on a file that uses `GlobalScope.launch` or `runBlocking` in a suspend function. The response should follow the playbook, reference the relevant `references/ref-*.md`, and use the output format (Analysis → Erroneous → Optimized → Explanation).

**Plugin layout:** The plugin root is `kotlin-coroutines-skill/`. It contains `.claude-plugin/plugin.json`, `skills/kotlin-coroutines/SKILL.md`, and `references/*.md`. The skill is namespaced (e.g. `kotlin-coroutines-skill:kotlin-coroutines` when listed).

---

## Example Prompts to Try

Use these to validate that the agent is following the skill:

1. **Scopes & leaks**  
   *“Refactor this code to avoid GlobalScope and follow structured concurrency.”*  
   (Use a snippet that uses `GlobalScope.launch` or `GlobalScope.async`.)

2. **Dispatchers**  
   *“I’m reading a file with `Dispatchers.Default`. Is that correct? Suggest an optimized version.”*

3. **Exceptions**  
   *“This catch block catches `Exception` and logs it. How should I handle CancellationException?”*

4. **Format check**  
   *“Review this coroutine code and give me: 1) Analysis, 2) Erroneous snippet, 3) Optimized snippet, 4) Explanation.”*

5. **Testing**  
   *“Replace runBlocking and real delay() in this test with kotlinx-coroutines-test and virtual time.”*

---

## Reference: Quick Checklist (from the skill)

The agent is instructed to enforce (among others):

- No `GlobalScope`; use framework or injected scopes.
- `async` only when you need a value; otherwise `launch`.
- No `runBlocking` inside suspend functions.
- Blocking I/O on `Dispatchers.IO` (e.g. `withContext(Dispatchers.IO)`).
- No `Dispatchers.Unconfined` in production.
- No `Job()` / `SupervisorJob()` passed directly to builders; use `supervisorScope` or a scope with `SupervisorJob()`.
- Do not swallow `CancellationException`; rethrow it in catch.
- Suspend cleanup in `finally` inside `withContext(NonCancellable)`.
- Tests use `runTest` and virtual time where possible.

Full checklist and rationale are in **SYSTEM_PROMPT.md**, in **SKILL.md** (playbook), in the **references/** files, and in the parent repo’s **docs/BEST_PRACTICES_COROUTINES.md**.

---

## License

MIT License. See [LICENSE](LICENSE) in this directory or the repo root.

---

## Contributing

Improvements to the system prompt, CONFIG, or examples that stay aligned with Kotlin’s structured concurrency and the existing checklist are welcome (e.g. via pull requests to the parent **structured-coroutines** repository).
