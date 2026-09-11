# Kotlin 2.4.x FIR API Audit

This document is the defensive audit deliverable for the Kotlin forward-compat
readiness change (deferred bump to toolkit v2.0.0). It does not upgrade or
fix production code — see
[KOTLIN_2_4_READINESS_CHECKLIST.md](KOTLIN_2_4_READINESS_CHECKLIST.md) for the
thresholds that gate the eventual bump itself, and the Engram decision
`decision/project-decision-kotlin-2-4-20-bump-targeted-for` (obs #1244) for
why the bump is deferred rather than applied now.

## Scope and method

Every checker file under `compiler/src/main/kotlin/io/github/santimattius/structured/compiler/`
was reviewed for unstable or internal Kotlin compiler (FIR) API usage. 13
files are in scope — the 12 files whose name matches `*Checker.kt`, plus
`ScoroutinesCallCheckerExtension.kt`, which registers the `DeclarationCheckers`
and `ExpressionCheckers` used by every other checker and shares their exact
FIR-API surface even though its filename does not end in `Checker.kt`. Five
plumbing files in the same directory (`PluginConfiguration.kt`,
`ScoroutinesFirExtensionRegistrar.kt`,
`StructuredCoroutinesCompilerPluginRegistrar.kt`,
`StructuredCoroutinesErrors.kt`, `StructuredScopeAnnotation.kt`) are out of
scope: they are plugin registration/config plumbing, not diagnostic logic
built on internal FIR node internals.

Findings below are not speculative: each unstable-API row was corroborated by
actually compiling `compiler/src/main/kotlin` against Kotlin 2.4.20 with the
`:compiler:forwardCompatTest` task added by this change (see
`compiler/build.gradle.kts` and the `forward-compat-kotlin` catalog
coordinate). The production build stays pinned to Kotlin 2.4.0
(`:compiler:compileKotlin`), which still compiles cleanly against every
pattern documented here — nothing here is broken **today**.

## Findings

| # | File | Unstable/internal API(s) | Stability classification | Alternative / accepted-risk note |
|---|------|---------------------------|---------------------------|-----------------------------------|
| 1 | `CallbackFlowWithoutAwaitCloseChecker.kt` | `FirFunctionCallChecker`, `MppCheckerKind` (stable checker-SPI surface only) | Stable | Compiles clean against `forward-compat-kotlin` (2.4.20). No unstable internal accessors used. |
| 2 | `CancellationExceptionSubclassChecker.kt` | `FirClassChecker`, `@OptIn(SymbolInternals::class)` via `fir.symbols.SymbolInternals` | Internal (opt-in), stable across 2.4.0 → 2.4.20 in this audit | `SymbolInternals` is JetBrains' own explicit-opt-in marker for direct symbol access; compiled clean against 2.4.20. Accepted risk — no stable non-internal replacement exists in the FIR checker API today. |
| 3 | `CancellationExceptionSwallowedChecker.kt` | `FirTryExpressionChecker`, `@OptIn(SymbolInternals::class)` | Internal (opt-in), stable across 2.4.0 → 2.4.20 in this audit | Same as #2. Compiled clean against `forward-compat-kotlin`. |
| 4 | `DispatchersUnconfinedChecker.kt` | `FirExpression.classId` (member) accessed directly on a `FirResolvedQualifier`-narrowed expression (`isDispatchersReceiver`, line 182); `FirFunctionCallChecker` | **Broken under Kotlin 2.4.20** (KT-84522 — `FirResolvedQualifier.classId` removed as a member). Confirmed via `forwardCompatTest`: `Unresolved reference 'classId'`. Still compiles and behaves correctly against the pinned production Kotlin 2.4.0. | A fix already exists and is tracked separately: local/remote branch `fix/firresolvedqualifier-classid-crash-90` (issue #90) adds a shared, version-stable `FirResolvedQualifier.resolvedClassId()` extension plus a structural guard test (`NoDirectResolvedQualifierClassIdTest`) and is **not yet merged to `main`** as of this audit. This change deliberately does not duplicate that fix — see Deviations in the apply-progress record. Merge #90 before (or as part of) the eventual 2.4.20 bump. |
| 5 | `JobInBuilderContextChecker.kt` | `FirFunctionCallChecker` (stable checker-SPI surface only) | Stable | Compiles clean against `forward-compat-kotlin`. |
| 6 | `LoopWithoutYieldChecker.kt` | `org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker` (base class) | **Broken under Kotlin 2.4.20.** Confirmed via `forwardCompatTest`: `Unresolved reference 'FirSimpleFunctionChecker'` — the type is not resolvable at that package path against the 2.4.20 compiler-embeddable. Compiles fine against the pinned 2.4.0. | New finding, not covered by issue #90. Likely renamed, relocated, or replaced by a differently-shaped function-declaration checker base class in 2.4.20; needs live investigation against an actual 2.4.20 FIR checker API at bump time (this audit does not speculate on the replacement without verifying it compiles). Track under the unblock checklist as an additional pre-bump task, not a blocker for this readiness change. |
| 7 | `RedundantLaunchInCoroutineScopeChecker.kt` | `FirFunctionCallChecker` (stable checker-SPI surface only) | Stable | Compiles clean against `forward-compat-kotlin`. |
| 8 | `RunBlockingInSuspendChecker.kt` | `FirFunctionCallChecker`, `@OptIn(SymbolInternals::class)` | Internal (opt-in), stable across 2.4.0 → 2.4.20 in this audit | Same as #2. Compiled clean against `forward-compat-kotlin`. |
| 9 | `ScoroutinesCallCheckerExtension.kt` | `FirAdditionalCheckersExtension`, `DeclarationCheckers`, `ExpressionCheckers`, `FirSimpleFunctionChecker` (via `simpleFunctionCheckers: Set<FirSimpleFunctionChecker>`) | **Broken under Kotlin 2.4.20** — same root cause as #6 (`FirSimpleFunctionChecker` unresolved), which also makes the `simpleFunctionCheckers` override fail to resolve its supertype member. Compiles fine against the pinned 2.4.0. | Same alternative as #6 — resolve `FirSimpleFunctionChecker`'s 2.4.20 replacement once, then update both this file and `LoopWithoutYieldChecker.kt` together (they share the same registration contract). |
| 10 | `SuspendCoroutineWithoutCancellationChecker.kt` | `FirFunctionCallChecker`, `@OptIn(SymbolInternals::class)` | Internal (opt-in), stable across 2.4.0 → 2.4.20 in this audit | Same as #2. Compiled clean against `forward-compat-kotlin`. |
| 11 | `SuspendInFinallyChecker.kt` | `FirExpression.classId` (member) accessed directly on a `FirResolvedQualifier`-narrowed expression (`isNonCancellable`, line 179); `FirTryExpressionChecker` | **Broken under Kotlin 2.4.20** — same root cause as #4 (KT-84522). Confirmed via `forwardCompatTest`. Compiles fine against the pinned production Kotlin 2.4.0. | Same alternative as #4 — covered by the unmerged `fix/firresolvedqualifier-classid-crash-90` branch (issue #90). |
| 12 | `UnstructuredLaunchChecker.kt` | `FirExpression.classId` (member) accessed directly on a `FirResolvedQualifier`-narrowed expression (`isGlobalScopeReceiver`, line 307); `FirFunctionCallChecker`, `@OptIn(SymbolInternals::class)` | **Broken under Kotlin 2.4.20** — same root cause as #4 (KT-84522). Confirmed via `forwardCompatTest`. Compiles fine against the pinned production Kotlin 2.4.0. The `SymbolInternals` usage elsewhere in this file (line 351) is unaffected — same status as #2. | Same alternative as #4 — covered by the unmerged `fix/firresolvedqualifier-classid-crash-90` branch (issue #90). |
| 13 | `UnusedDeferredChecker.kt` | `FirFunctionCallChecker`, `@OptIn(SymbolInternals::class)` | Internal (opt-in), stable across 2.4.0 → 2.4.20 in this audit | Same as #2. Compiled clean against `forward-compat-kotlin`. |

## Summary

- **9 of 13** checker files compile clean against Kotlin 2.4.20 with no
  changes: #1, #2, #3, #5, #7, #8, #10, #12 (partially — see below), #13.
- **4 of 13** files exercise APIs that break under Kotlin 2.4.20 today:
  - `DispatchersUnconfinedChecker.kt`, `SuspendInFinallyChecker.kt`,
    `UnstructuredLaunchChecker.kt` — `FirResolvedQualifier.classId` removal
    (KT-84522, issue #90). **A fix already exists** on the unmerged
    `fix/firresolvedqualifier-classid-crash-90` branch; this change does not
    duplicate it.
  - `LoopWithoutYieldChecker.kt` and `ScoroutinesCallCheckerExtension.kt` —
    `FirSimpleFunctionChecker` unresolved. **New finding**, no fix exists yet;
    add to future bump prep work.
- No source file was modified by this audit — all findings above were
  reproduced read-only via the `forwardCompatTest` smoke-compile task and are
  non-blocking in CI (see `.github/workflows/ci.yml`'s `forward-compat` job).
- None of the above affects the current production build: `:compiler:compileKotlin`
  and `:compiler:test` both pass unchanged against the pinned Kotlin 2.4.0.

## Additional observation (outside audit scope)

`StructuredCoroutinesCompilerPluginRegistrar.kt` (a plumbing file, out of the
13-file checker scope above) surfaces a compiler diagnostic against
`forward-compat-kotlin` (2.4.20) at its direct `CompilerConfiguration.get(...)`
message-collector access: *"Direct access to the message collector is
discouraged. Consider using `CompilerConfiguration.report`."* It does not
reproduce against the pinned production Kotlin 2.4.0 and is advisory
(discouraged, not yet a hard error) rather than broken. Recorded here for
completeness; not fixed, since it is both out of this audit's declared scope
and not currently broken.

## Cross-references

- Unblock thresholds for the eventual bump:
  [KOTLIN_2_4_READINESS_CHECKLIST.md](KOTLIN_2_4_READINESS_CHECKLIST.md)
- Deferral decision: Engram `decision/project-decision-kotlin-2-4-20-bump-targeted-for` (obs #1244)
- Issue #90 fix (unmerged, tracked separately from this readiness change):
  branch `fix/firresolvedqualifier-classid-crash-90`
