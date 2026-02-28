# Kotlin Coroutines Skill

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Version](https://img.shields.io/badge/skill%20version-1.0.0-blue.svg)]()

Expert guidance for any AI coding tool that supports Agent Skills or custom instructions — **safe structured concurrency**, performance, and Kotlin 1.9/2.0+ best practices for Coroutines.

This skill is part of the [Structured Coroutines](https://github.com/santimattius/structured-coroutines) project. It encodes a single set of rules (scopes, dispatchers, exceptions, cancellation, testing, channels) so that Claude, ChatGPT, Cursor, or other agents give **consistent, correct** advice on Kotlin Coroutines.

Inspired by the [Swift Concurrency Agent Skill](https://github.com/AvdLee/Swift-Concurrency-Agent-Skill) model.

---

## Why This Skill Exists

- **Structured Concurrency is easy to get wrong:** `GlobalScope`, wrong Dispatchers, swallowed `CancellationException`, and misuse of `SupervisorJob` lead to leaks, ANRs, and flaky behavior. Many AI answers repeat these mistakes.
- **One source of truth:** This skill encodes a consistent checklist so every AI tool gives the same aligned recommendations.
- **Faster reviews and migrations:** Teams can point their AI at this skill and get code that follows the same rules — no GlobalScope, proper scopes, `withContext(IO)`, virtual-time tests, etc.

---

## What's Included

| Asset | Description |
|-------|-------------|
| **SYSTEM_PROMPT.md** | Full system prompt: identity, strict rules, tone, and required output format (analysis → erroneous code → optimized code → explanation). |
| **SKILL.md** | Playbook (triage): maps topic/error to the right reference file so the agent jumps to the relevant practice. |
| **references/** | One markdown file per best practice (19 files). Each has Bad / Recommended / Why / Quick fix. |
| **CONFIG.json** | Metadata: name, description, version, triggers, and reference index. |
| **EXAMPLES_SUITE.kt** | Kotlin examples with intentional anti-patterns (scope leaks, exception handling, Dispatchers) for testing the agent. |

### References (per practice)

| # | Topic | File |
|---|-------|------|
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

---

## Setup

### Option A: Claude Code (Plugin — Recommended)

Claude Code natively supports this skill as a plugin via the marketplace.

**Requirements:** [Claude Code](https://code.claude.com/docs) installed and authenticated; version **1.0.33 or later** (`claude --version`).

#### Install from the marketplace

```bash
/plugin marketplace add santimattius/structured-coroutines
/plugin install kotlin-coroutines-skill
```

#### Install from a local directory

```bash
claude --plugin-dir /path/to/structured-coroutines/kotlin-coroutines-skill
```

Replace the path with the actual location of this `kotlin-coroutines-skill` folder.

#### How it works

Once installed, the skill is available automatically when you work on Kotlin/Android code. Claude Code reads:

- `SKILL.md` — triage playbook (maps your topic/error to the right reference)
- `SYSTEM_PROMPT.md` — strict rules and output format
- `references/ref-*.md` — per-practice guidance loaded on demand

**Plugin layout:**

```
repo root/
├── .claude-plugin/
│   ├── plugin.json          ← plugin definition (skills: ["./kotlin-coroutines-skill"])
│   └── marketplace.json     ← marketplace catalog (owner + plugins)
└── kotlin-coroutines-skill/
    ├── SKILL.md
    ├── SYSTEM_PROMPT.md
    ├── CONFIG.json
    ├── EXAMPLES_SUITE.kt
    └── references/
```

**Verify installation:** Open a `.kt` file containing `GlobalScope.launch { }` and ask Claude Code to review it for coroutine best practices. The response should follow the format: **Analysis → Erroneous Code → Optimized Code → Technical Explanation**.

---

### Option B: Claude (Projects — System Prompt)

Use this when you want the skill active for a specific Claude project without the Claude Code CLI.

1. In Claude, open **Projects** and create or select a project.
2. Go to **Project settings → Custom instructions**.
3. Paste the full content of **SYSTEM_PROMPT.md**.
4. Optionally add: *"For Kotlin Coroutines questions, always use the structured output format: 1) Analysis, 2) Erroneous code, 3) Optimized code, 4) Technical explanation."*
5. Save.

Use this project when working on Kotlin/Android codebases for consistent advice.

---

### Option C: ChatGPT (Custom GPTs)

1. Create a new **Custom GPT** (ChatGPT Plus or Team).
2. In **Configure → Instructions**, paste the full content of **SYSTEM_PROMPT.md**.
3. Optionally upload the `references/` files to **Knowledge** so the GPT can reference them.
4. Save and name the GPT (e.g. "Kotlin Coroutines Expert").

**Quick test:** Ask: *"Why shouldn't I use GlobalScope.launch in an Android ViewModel?"* — You should get analysis, bad snippet, good snippet, and explanation.

---

### Option D: Cursor (Rules for AI)

1. In your repo, open or create `.cursor/rules/`.
2. Create a file `kotlin-coroutines.mdc`.
3. Paste the full content of **SYSTEM_PROMPT.md**.
4. Add a `globs` condition so the rule applies to Kotlin files: `**/*.kt`.
5. Reload Cursor rules.

**Verify:** Open a `.kt` file with `GlobalScope.launch { }` and ask Cursor to refactor it. The answer should follow the skill's format and rules.

---

## Example Prompts

Use these to validate that the agent is following the skill:

| # | Prompt | What it tests |
|---|--------|---------------|
| 1 | *"Refactor this code to avoid GlobalScope and follow structured concurrency."* | Scopes & leaks |
| 2 | *"I'm reading a file with `Dispatchers.Default`. Is that correct?"* | Dispatchers |
| 3 | *"This catch block catches `Exception` and logs it. How should I handle CancellationException?"* | Exception handling |
| 4 | *"Replace runBlocking and real delay() in this test with kotlinx-coroutines-test and virtual time."* | Testing |
| 5 | *"Review this coroutine code: 1) Analysis, 2) Erroneous snippet, 3) Optimized snippet, 4) Explanation."* | Output format |

---

## Quick Checklist

The skill enforces these rules in every response:

- No `GlobalScope`; use framework (`viewModelScope`, `lifecycleScope`) or injected/local scopes.
- `async` only when you need a return value; otherwise `launch`.
- No `runBlocking` inside suspend functions — use `withContext` or `coroutineScope`.
- Blocking I/O always on `withContext(Dispatchers.IO)`. Never on `Default` or `Main`.
- No `Dispatchers.Unconfined` in production.
- No `Job()` / `SupervisorJob()` passed directly to builders; use `supervisorScope { }` or a scope-level `SupervisorJob`.
- Never swallow `CancellationException`; rethrow it in catch.
- Suspend cleanup in `finally` → `withContext(NonCancellable) { }`.
- Do not reuse a scope after `scope.cancel()`; use `cancelChildren()` to stop only children.
- Tests use `runTest` with virtual time (`advanceTimeBy`, `advanceUntilIdle`). No real `delay()`.
- Channels: prefer `produce { }`. Use `for (x in channel)` per consumer, not `consumeEach` for fan-out.

Full rules are in **SYSTEM_PROMPT.md**, the triage table is in **SKILL.md**, and per-practice detail is in `references/`.

---

## License

MIT License. See [LICENSE](LICENSE) in this directory or the repo root.

---

## Contributing

Improvements to the system prompt, CONFIG, or reference files that stay aligned with Kotlin's structured concurrency and the existing checklist are welcome via pull requests to the parent [structured-coroutines](https://github.com/santimattius/structured-coroutines) repository.
