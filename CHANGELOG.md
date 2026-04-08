# Changelog

All notable changes to **Structured Coroutines** are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and versions track [Semantic Versioning](https://semver.org/).

---

## [0.7.0] — 2026-04-07

### Changed — Compiler

- **Kotlin 2.3.20** — `libs.versions.toml` bumped from `2.3.0` to `2.3.20`; functional test updated to compile against the new toolchain.
- **FIR API migration** — all compiler checkers adapted to breaking API changes introduced in Kotlin 2.3.20:
  - `ConeKotlinType.toClassSymbol` (removed) replaced with `(type as? ConeClassLikeType)?.lookupTag?.classId` across `CancellationExceptionSubclassChecker`, `CancellationExceptionSwallowedChecker`, `RunBlockingInSuspendChecker`, and `UnusedDeferredChecker`.
  - `context.containingDeclarations` now returns `List<FirBasedSymbol<*>>`; `.fir` access annotated with `@OptIn(SymbolInternals::class)` added where needed.
  - `FirSimpleFunction` references updated to `FirNamedFunction` (declarations package) where the compiler requires the concrete type.
  - `LoopWithoutYieldChecker` — minor call-site adjustment for 2.3.20 API shape.

### Fixed — Lint rules

- **`CoroutineLintUtils`** — `UParenthesizedExpression.expression` and `UQualifiedReferenceExpression.selector` usages updated to non-null access following UAST nullable-to-non-null promotions in the new toolchain.
- **`ChannelNotClosedDetector`** / **`LifecycleAwareFlowCollectionDetector`** — minor utility call updates for compatibility.

---

## [0.6.1] — 2026-03-22

### Fixed — Compiler

- **`UnstructuredLaunchChecker`** — local variables initialized from framework scope functions (e.g. `rememberCoroutineScope()`) are now recognized as structured scopes and no longer flagged as unstructured launches.

### Fixed — IntelliJ plugin

- **`CoroutineInspectionBase`** — all inspections now honor `@Suppress("RuleName")` on the flagged element and `@file:Suppress` at the file level, eliminating false positives in intentionally suppressed code.
- **`GlobalScopeInspection`** — `getShortName()` override added to align the runtime ID with the registered inspection ID, fixing suppression not working for this rule.
- **`CoroutinePsiUtils`** — new PSI utilities to detect local variables initialized from framework scope functions.

### Fixed — Lint rules

- **`CoroutineLintUtils`** — parenthesized receivers are now unwrapped before analysis; PSI fallback added to detect call- and name-based framework scopes (e.g. `rememberCoroutineScope()`); new helper traces local variable initializers for accurate scope detection.
- **`UnstructuredLaunchDetector`** — inline `CoroutineScope` construction detection improved; new test coverage with a Compose runtime stub verifying true-positive and false-positive guard cases.

---

## [0.6.0] — 2026-03-19

### Added — Gradle plugin

- **`structuredCoroutinesReport` task** — new `@CacheableTask` (group `reporting`) that generates an HTML and a plain-text report of the active compiler-plugin configuration. Reports are written to `build/reports/structured-coroutines/` and list all 12 rule codes with their configured severity and a direct link to the relevant section of the best-practices guide.
- **`reportOutputDir`** and **`reportFormat`** (`"html"` | `"text"` | `"all"`) extension properties with sensible defaults.
- **[`docs/CI_INTEGRATION.md`](docs/CI_INTEGRATION.md)** — full GitHub Actions workflow reference covering SARIF upload, artifact archiving, and PR comment automation.

### Added — IntelliJ plugin

- **"Scan Project for Coroutine Issues" action** — available in the **Analyze** menu and as a toolbar button in the Structured Coroutines tool window. Runs all 13 inspections across every Kotlin source file in a background thread, reports real-time progress per file, and populates the tool window with aggregated findings.
- **`StructuredCoroutinesInspectionRunner`** — singleton engine used by the scan action. Uses `StructuredCoroutinesInspectionProvider` as the single source of truth (new inspections are automatically included), filters out `build/` and generated-code directories, and wraps all PSI access in `ReadAction.compute` for thread safety.
- **`CoroutinesImportFilter`** (IntelliJ) — fast import-based guard that short-circuits all 13 inspections when a file does not import `kotlinx.coroutines`, eliminating false positives from name collisions with non-coroutines APIs (e.g. `ActivityScenario.launch`, custom `async {}`).

### Added — Detekt

- **`CoroutinesImportFilter`** (Detekt) — same guard applied to all 19 Detekt rules; files without a `kotlinx.coroutines` import are skipped before any analysis runs.

### Fixed

- **`SuspendInFinallyInspection`** — replaced the brittle hard-coded `nonSuspendCalls` blocklist (`println`, `let`, `run`, …) with a proper delegation to `CoroutinePsiUtils.isSuspendCall`, which uses IntelliJ's type-resolution pipeline.
- **`ScopeAnalyzer`** — `findScopeDeclarationByName` now resolves `@StructuredScope`-annotated scopes injected via primary constructor parameters.
- **`sample-detekt`** — corrected `consumeEach` import path in `ConsumeEachMultipleConsumersExample.kt`.

### Changed

- Plugin and project version bumped to **0.6.0**.

---

## [0.5.0] — 2026-03-09

### Added — IntelliJ plugin

- **Tool window "What to do"** — the Structured Coroutines tool window now shows a short actionable summary and an inline "See guide →" link for every finding. Implemented via a new `InspectionGuideRegistry` that maps all 15 inspections to a `GuideEntry` (`whatToDo` text + `guideUrl`).
- **`WithTimeoutScopeCancellationInspection`** — detects `withTimeout` calls outside a `try/catch` that handles `TimeoutCancellationException`; mirrors the new Detekt rule.
- **`ReplaceWithTimeoutOrNullQuickFix`** — one-click replacement of `withTimeout` with `withTimeoutOrNull`.
- **Extended `AsyncWithoutAwait` description** — now cites §5.3 EXCEPT_003 to explain that exceptions inside `async` are deferred until `await()` and are not propagated to `CoroutineExceptionHandler`.

### Added — Detekt

- **`WithTimeoutScopeCancellationRule`** (`CANCEL_006` §4.6, severity `Warning`) — heuristic that flags `withTimeout` not wrapped in a try/catch covering `TimeoutCancellationException`, `CancellationException`, `Exception`, or `Throwable`. Suppress with `@Suppress("WithTimeoutScopeCancellation")` when scope cancellation is intentional.

### Added — Documentation

- **`docs/DECISION_GUIDE.md`** — 10 decision sections with tables, trees, and code examples covering: `launch` vs `async`, which scope to use, `viewModelScope` vs `lifecycleScope`, `runTest` vs `runBlocking`, dispatcher selection, error handling, `cancel()` vs `cancelChildren()`, loops, `withTimeout` vs `withTimeoutOrNull`, and cold vs hot Flow.
- **`kotlin-coroutines-skill` v2.0.0** — 13 new reference files covering §§1.4, 3.2, 3.5, 4.2, 4.6–4.7, 5.3, 6.3, 8.2, 9.1–9.4. `SKILL.md` triage table extended to 34 entries; `SYSTEM_PROMPT.md` updated with strict rules for all new practices; `CONFIG.json` version → `2.0.0`, `referenceIndex` → 32 files.

### Fixed

- **IDE compatibility** — extended support for IntelliJ builds `253.*` (Android Studio / IntelliJ AI 253.x).
- **`plugin.xml`** — `LoopWithoutYieldInspection` `implementationClass` corrected.

---

## [0.4.0] — 2026-02-26

### Added — New rules (all layers)

| Rule | Code | § | Detekt | Lint | IDE |
|------|------|---|:------:|:----:|:---:|
| ChannelNotClosed | CHANNEL_001 | 7.1 | ✅ | ✅ | — |
| ConsumeEachMultipleConsumers | CHANNEL_002 | 7.2 | ✅ | ✅ | — |
| LoopWithoutYield | CANCEL_001 | 4.1 | existing | existing | ✅ quick fix |
| FlowBlockingCall | FLOW_001 | 9.1 | ✅ | ✅ | — |
| LifecycleAwareFlowCollection | ARCH_002 | 8.2 | — | ✅ | ✅ |

- **`ChannelNotClosedRule` / `ChannelNotClosedDetector`** — detects manual `Channel()` creation without a `close()` call; recommends `produce { }`.
- **`ConsumeEachMultipleConsumersRule` / `ConsumeEachMultipleConsumersDetector`** — detects `consumeEach` on the same channel from multiple coroutines; recommends `for (value in channel)` for fan-out.
- **`LoopWithoutYieldChecker`** (Compiler, FIR) — `LOOP_WITHOUT_YIELD` warning for `while`/`do-while` in suspend functions without a cooperation point (`yield`, `ensureActive`, `delay`). EN/ES messages; configurable via `structuredCoroutines { loopWithoutYield = ... }`.
- **`LoopWithoutYieldInspection`** (IDE) — mirrors the compiler checker; quick fixes insert `ensureActive()`, `currentCoroutineContext().ensureActive()`, `yield()`, or `delay(0)` at the start of the loop body.
- **`FlowBlockingCallRule` / `FlowBlockingCallDetector`** — detects `Thread.sleep`, synchronous I/O, JDBC, and similar blocking calls inside `flow { }`.
- **`LifecycleAwareFlowCollectionInspection` / `LifecycleAwareFlowCollectionDetector`** — detects Flow collection (`collect`, `collectLatest`) in `lifecycleScope.launch` without `repeatOnLifecycle` or `flowWithLifecycle` inside a `LifecycleOwner`.

### Added — IDE

- **`ConvertToRunTestIntention`** — intention available when the cursor is inside `runBlocking { }` containing `delay()`; replaces it with `runTest` for virtual-time test execution.
- **`ChangeSuperclassToExceptionQuickFix`** — replaces `CancellationException` superclass with `Exception` in domain error classes (CANCEL_005, §4.2).
- **`ScopeReuseAfterCancelInspection`** messages reinforced — description and error text explicitly guide the developer to apply `ReplaceCancelWithCancelChildrenQuickFix`.

### Fixed

- **SCOPE_002 (UnusedDeferred / AsyncWithoutAwait)** — `Deferred` is no longer reported as unused when passed to `awaitAll` (e.g. `deferredList.awaitAll()`).
- **RUNBLOCK_001 (RedundantLaunchInCoroutineScope)** — the single `launch` inside a `forEach`/`for`/`while` loop is no longer reported as redundant (the scope correctly waits for all iterations).

---

## [0.3.0] — 2026-02-17

### Added — Rule codes and documentation links

- Every diagnostic across all four layers (Compiler, Detekt, Lint, IDE) now includes a **rule code** (`SCOPE_001`, `CANCEL_003`, etc.) and a **direct link** to the relevant anchor in `BEST_PRACTICES_COROUTINES.md`. Developers can identify a rule and open its documentation in one click.
- `docs/rule-codes.yml` — machine-readable registry of all rule codes, titles, sections, and suppression IDs.
- `docs/SUPPRESSING_RULES.md` — unified suppression IDs usable across all tools (`@Suppress`, `// noinspection`, `baseline.xml`).

### Added — Gradle plugin

- **Configuration profiles** — `useStrictProfile()`, `useGradualProfile()`, `useRelaxedProfile()` presets that set severity levels for all rules in one call.
- **`excludeSourceSets(vararg names)`** and **`excludeProjects(vararg names)`** — skip the plugin for legacy modules without disabling it project-wide.
- `docs/GRADUAL_ADOPTION.md` — step-by-step migration guide (relaxed → gradual → strict), exclusion examples, and suppression best practices.

### Added — Detekt

- New and optimized rules aligned with the Tool Implementation Matrix in `BEST_PRACTICES_COROUTINES.md`.
- Rule descriptions standardized with `[CODE]` prefix and doc link format.

### Changed

- Rule sync across Compiler, Detekt, and Android Lint — consistent naming, codes, and suppression IDs.

---

## [0.2.0] — 2026-02-08

### Added — IntelliJ plugin

- **Structured Coroutines tool window** — project panel listing all active inspection findings with severity, file, and line information. Supports double-click navigation to the source location.
- IDE compatibility extended to **IntelliJ IDEA 2024.3–2025.x** (builds 243–252).

### Added — Kotlin Coroutines Agent Skill

- **`kotlin-coroutines-skill` v1.0.0** — AI/agent guidance system with 19 reference documents, a system prompt, and a triage playbook covering structured concurrency, scopes, Dispatchers, cancellation, exception handling, and testing.

### Added — Samples and tooling

- Compilation sample project (`:sample`) with intentional violations and a `validateSample` Gradle task to assert expected compiler errors.
- `pluginCheckTask` and unified project-version propagation in the Gradle plugin.

### Fixed

- Android Lint rules publish configuration.

---

## [0.1.0] — 2026-02-01

### Added — Initial release

**Compiler plugin (`:compiler`)**
- K2/FIR compiler plugin with **12 rules** enforced at compile time via `FirSimpleFunctionChecker` and `FirCallChecker`.
- Configurable severity per rule (error / warning / none) via the `structuredCoroutines {}` Gradle DSL.
- EN/ES message bundles; locale configurable via JVM args.

| Rule | Code | § |
|------|------|---|
| GlobalScope in production | SCOPE_001 | 1.1 |
| async without await | SCOPE_002 | 1.2 |
| Breaking structured concurrency (launch) | SCOPE_003 | 1.3 |
| Breaking structured concurrency (inline scope) | SCOPE_003 | 1.3 |
| Redundant launch in coroutineScope | RUNBLOCK_001 | 2.1 |
| runBlocking in suspend functions | RUNBLOCK_002 | 2.2 |
| Dispatchers.Unconfined abuse | DISPATCH_003 | 3.3 |
| Job() as coroutine context | DISPATCH_004 | 3.4 |
| Loop without yield | CANCEL_001 | 4.1 |
| Swallowing CancellationException | CANCEL_003 | 4.3 |
| Suspendable cleanup without NonCancellable | CANCEL_004 | 4.4 |
| CancellationException subclass for domain errors | EXCEPT_002 | 5.2 |

**Detekt rules (`:detekt-rules`)**
- **18 rules** covering scopes, dispatchers, cancellation, exception handling, channels, and Flow.
- Each rule includes a `[CODE]` prefix in its description and a link to the best-practices guide.

**Android Lint rules (`:lint-rules`)**
- **21 rules** targeting the same anti-patterns as Detekt; includes automated quick fixes for the most common issues.
- 4 Android-specific rules (e.g. lifecycle-aware collection).

**IntelliJ / Android Studio plugin (`:intellij-plugin`)**
- **11 inspections** with real-time feedback; **9 quick fixes** for automatic corrections; **5 intentions** for refactoring; **2 gutter icon providers**.
- Full K2 compiler mode support.
- Compatible with IntelliJ IDEA 2024.3+ and Android Studio Meerkat+.

**Gradle plugin (`:gradle-plugin`)**
- `structuredCoroutines {}` DSL extension to configure rule severity.
- Applied automatically alongside the Kotlin compiler plugin.

**Annotations (`:annotations`)**
- `@StructuredScope` — marks a `CoroutineScope` as the intended structured scope for a component; used by compiler and IDE inspections.
- Kotlin Multiplatform (JVM, Android, iOS, JS, Linux).

**Documentation**
- `docs/BEST_PRACTICES_COROUTINES.md` — canonical best-practices guide (~55 KB) covering 9 sections and 30+ practices with code examples and a Tool Implementation Matrix.

---

[0.7.0]: https://github.com/santimattius/structured-coroutines/compare/0.6.1...0.7.0
[0.6.1]: https://github.com/santimattius/structured-coroutines/compare/0.6.0...0.6.1
[0.6.0]: https://github.com/santimattius/structured-coroutines/compare/0.5.0...0.6.0
[0.5.0]: https://github.com/santimattius/structured-coroutines/compare/0.4.0...0.5.0
[0.4.0]: https://github.com/santimattius/structured-coroutines/compare/0.3.0...0.4.0
[0.3.0]: https://github.com/santimattius/structured-coroutines/compare/0.2.0...0.3.0
[0.2.0]: https://github.com/santimattius/structured-coroutines/compare/0.1.0...0.2.0
[0.1.0]: https://github.com/santimattius/structured-coroutines/releases/tag/0.1.0
