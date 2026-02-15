# Rule coverage: Compiler, Detekt, and Lint

This document describes **what each tool covers** and **its limitations** for structured-coroutines rules. Use it to choose the right tool(s) and to look up suppression IDs.

**References:** [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md) (suppression IDs per tool), [BEST_PRACTICES_COROUTINES.md](BEST_PRACTICES_COROUTINES.md) (rule codes and practices), [rule-codes.yml](rule-codes.yml) (canonical list of codes and IDs).

---

## 1. Coverage matrix (by rule code)

| Rule code | Practice | Compiler | Detekt | Lint |
|-----------|----------|:--------:|:------:|:----:|
| SCOPE_001 | GlobalScope usage | ✅ | ✅ | ✅ |
| SCOPE_002 | async without await | ✅ | ✅ | ✅ |
| SCOPE_003 | Inline scope / unstructured launch | ✅ | ✅ | ✅ |
| RUNBLOCK_001 | Redundant launch in coroutineScope | ✅ | ✅ | ✅ |
| RUNBLOCK_002 | runBlocking in suspend | ✅ | ✅ | ✅ |
| DISPATCH_001 | Blocking call in coroutine | — | ✅ | ✅ |
| DISPATCH_003 | Dispatchers.Unconfined | ✅ | ✅ | ✅ |
| DISPATCH_004 | Job() in builder context | ✅ | ✅ | ✅ |
| CANCEL_003 | Swallowing CancellationException | ✅ | ✅ | ✅ |
| CANCEL_004 | Suspend in finally without NonCancellable | ✅ | ✅ | ✅ |
| CANCEL_005 | Scope reuse after cancel | — | ✅ | ✅ |
| CANCEL_001 | Loop without yield | — | ✅ | ✅ |
| EXCEPT_002 | CancellationException subclass | ✅ | ✅ | ✅ |
| TEST_001 | runBlocking + delay in test | — | ✅ | ✅ |
| ARCH_002 | Lifecycle-aware scope (Android) | — | — | ✅ |

**Legend:** ✅ = implemented; — = not implemented.

---

## 2. Compiler plugin

### What it covers

Reports at **compile time** (all platforms):

- **SCOPE_001** — GlobalScope.launch/async  
- **SCOPE_002** — async without await  
- **SCOPE_003** — Inline scope (e.g. `CoroutineScope(...).launch`) and launch on scopes not annotated with `@StructuredScope`  
- **RUNBLOCK_001** — Redundant launch in coroutineScope/supervisorScope  
- **RUNBLOCK_002** — runBlocking in suspend functions  
- **DISPATCH_003** — Dispatchers.Unconfined  
- **DISPATCH_004** — Job()/SupervisorJob() in builder context  
- **CANCEL_003** — catch(Exception) that may swallow CancellationException  
- **CANCEL_004** — Suspend calls in finally without withContext(NonCancellable)  
- **EXCEPT_002** — Class extends CancellationException  

### Limitations

| Limitation | Reason |
|------------|--------|
| No DISPATCH_001 (blocking in coroutine) | Would require blocking-call and coroutine-context analysis; not in scope for the compiler plugin. |
| No CANCEL_001 (loop without yield) | Needs control-flow heuristics; implemented in static analyzers instead. |
| No CANCEL_005 (scope reuse after cancel) | Requires data-flow/usage analysis. |
| No TEST_001 (runBlocking + delay in tests) | Would require test-file or test-symbol awareness. |
| No ARCH_002 (Android lifecycle) | Platform-specific; compiler is multiplatform. |
| SCOPE_003 | Compiler enforces **@StructuredScope**; Detekt/Lint use heuristics (e.g. external scope from suspend, inline scope). Compiler is stricter. |

---

## 3. Detekt

### What it covers

All rules that the compiler covers (see §2), plus:

- **DISPATCH_001** — Blocking call in coroutine (e.g. `Thread.sleep`) — rule name: `BlockingCallInCoroutine`  
- **CANCEL_001** — Loop without cooperation point — `LoopWithoutYield`  
- **CANCEL_005** — Scope reuse after cancel — `ScopeReuseAfterCancel`  
- **TEST_001** — runBlocking + delay in test code — `RunBlockingWithDelayInTest`  

Runs on **Kotlin** (JVM, MPP, etc.); no Android SDK.

### Limitations

| Limitation | Reason |
|------------|--------|
| No ARCH_002 (LifecycleAwareScope, ViewModelScopeLeak) | Android-specific (Lifecycle, ViewModel); Detekt is generic Kotlin. |
| SCOPE_003 | Implements **InlineCoroutineScope** and **ExternalScopeLaunch** (heuristics). Does **not** check @StructuredScope like the compiler. |

---

## 4. Android Lint

### What it covers

All rules that Detekt covers for structured-coroutines, plus:

- **ARCH_002** — Lifecycle-aware scope / ViewModelScope leak — `LifecycleAwareScope`, `ViewModelScopeLeak` (Android only).

Runs in **Android** (and Android-aware Gradle) projects.

### Limitations

| Limitation | Reason |
|------------|--------|
| Android only | Does not run for plain JVM/MPP modules without Android. |
| DISPATCH_001 | Lint uses **MainDispatcherMisuse** (main-thread blocking); Detekt uses **BlockingCallInCoroutine** (broader list). Semantics are similar but not identical. |
| Some rules | IntelliJ inspections may not exist for RUNBLOCK_001, CANCEL_001, TEST_001; see [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md). |

---

## 5. Naming differences (suppression IDs)

Same practice can have different IDs per tool. When suppressing, use the ID of the **tool that reports** (see [SUPPRESSING_RULES.md](SUPPRESSING_RULES.md)).

| Rule code | Compiler | Detekt | Lint |
|-----------|----------|--------|------|
| SCOPE_002 | `UNUSED_DEFERRED` | `UnusedDeferred` | `AsyncWithoutAwait` |
| SCOPE_003 | `UNSTRUCTURED_COROUTINE_LAUNCH`, `INLINE_COROUTINE_SCOPE` | `ExternalScopeLaunch`, `InlineCoroutineScope` | `UnstructuredLaunch`, `InlineCoroutineScope` |
| DISPATCH_001 | — | `BlockingCallInCoroutine` | `MainDispatcherMisuse` |

---

## 6. When to use which tool

| Goal | Use |
|------|-----|
| Enforce at compile time, all platforms | **Compiler** (via [Gradle plugin](../gradle-plugin/README.md)) |
| Kotlin JVM/MPP without Android | **Detekt** (optionally with Compiler) |
| Android app or library | **Lint** (optionally with Compiler and Detekt) |
| Scope reuse after cancel (CANCEL_005) | **Detekt** or **Lint** |
| Lifecycle/ViewModel scope (ARCH_002) | **Lint** only (Android) |
| @StructuredScope enforcement (SCOPE_003) | **Compiler** only (Detekt/Lint use heuristics) |
