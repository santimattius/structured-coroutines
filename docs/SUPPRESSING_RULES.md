# Suppressing Structured Coroutines Rules

This document lists the **unified suppression identifiers** for each rule across the Compiler
Plugin, Detekt, Android Lint, and IntelliJ inspections. Use it to suppress a specific rule when you
have a justified exception.

Rule codes (e.g. `SCOPE_001`) and full descriptions are
in [BEST_PRACTICES_COROUTINES.md](BEST_PRACTICES_COROUTINES.md#rule-codes-reference). The canonical
list of codes and suppression IDs per tool is in [rule-codes.yml](rule-codes.yml).

---

## Suppression identifier by tool

| Rule code    | §   | Compiler `@Suppress`                                      | Detekt `@Suppress`                            | Lint `@SuppressLint`                         | IntelliJ `@Suppress`                         |
|--------------|-----|-----------------------------------------------------------|-----------------------------------------------|----------------------------------------------|----------------------------------------------|
| SCOPE_001    | 1.1 | `GLOBAL_SCOPE_USAGE`                                      | `GlobalScopeUsage`                            | `GlobalScopeUsage`                           | `GlobalScopeUsage`                           |
| SCOPE_002    | 1.2 | `UNUSED_DEFERRED`                                         | `UnusedDeferred`                              | `AsyncWithoutAwait`                          | `AsyncWithoutAwait`                          |
| SCOPE_003    | 1.3 | `UNSTRUCTURED_COROUTINE_LAUNCH`, `INLINE_COROUTINE_SCOPE` | `ExternalScopeLaunch`, `InlineCoroutineScope` | `UnstructuredLaunch`, `InlineCoroutineScope` | `UnstructuredLaunch`, `InlineCoroutineScope` |
| RUNBLOCK_001 | 2.1 | `REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE`                     | `RedundantLaunchInCoroutineScope`             | `RedundantLaunchInCoroutineScope`            | —                                            |
| RUNBLOCK_002 | 2.2 | `RUN_BLOCKING_IN_SUSPEND`                                 | `RunBlockingInSuspend`                        | `RunBlockingInSuspend`                       | `RunBlockingInSuspend`                       |
| DISPATCH_001 | 3.1 | —                                                         | `BlockingCallInCoroutine`                     | `MainDispatcherMisuse`                       | `MainDispatcherMisuse`                       |
| DISPATCH_003 | 3.3 | `DISPATCHERS_UNCONFINED_USAGE`                            | `DispatchersUnconfined`                       | `DispatchersUnconfined`                      | `DispatchersUnconfined`                      |
| DISPATCH_004 | 3.4 | `JOB_IN_BUILDER_CONTEXT`                                  | `JobInBuilderContext`                         | `JobInBuilderContext`                        | `JobInBuilderContext`                        |
| CANCEL_003   | 4.3 | `CANCELLATION_EXCEPTION_SWALLOWED`                        | `CancellationExceptionSwallowed`              | `CancellationExceptionSwallowed`             | `CancellationExceptionSwallowed`             |
| CANCEL_004   | 4.4 | `SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE`              | `SuspendInFinally`                            | `SuspendInFinally`                           | `SuspendInFinally`                           |
| CANCEL_005   | 4.5 | —                                                         | `ScopeReuseAfterCancel`                       | `ScopeReuseAfterCancel`                      | `ScopeReuseAfterCancel`                      |
| CANCEL_001   | 4.1 | `LOOP_WITHOUT_YIELD`                                      | `LoopWithoutYield`                            | `LoopWithoutYield`                           | `LoopWithoutYield`                            |
| EXCEPT_002   | 5.2 | `CANCELLATION_EXCEPTION_SUBCLASS`                         | `CancellationExceptionSubclass`               | `CancellationExceptionSubclass`              | `CancellationExceptionSubclass`              |
| TEST_001     | 6.1 | —                                                         | `RunBlockingWithDelayInTest`                  | `RunBlockingWithDelayInTest`                 | —                                            |
| ARCH_002     | 8.2 | —                                                         | —                                             | `LifecycleAwareScope`, `ViewModelScopeLeak`, `LifecycleAwareFlowCollection` | `LifecycleAwareFlowCollection`                |
| FLOW_001     | 9.1 | —                                                         | `FlowBlockingCall`                            | `FlowBlockingCall`                           | —                                            |

**Note:** “—” means the rule is not implemented in that tool. When the same practice is reported by
more than one diagnostic (e.g. SCOPE_003), suppress each identifier that applies to the code you’re
suppressing.

---

## How to suppress

### Compiler Plugin (Kotlin)

Use Kotlin’s `@Suppress` with the **Compiler** identifier from the table. The compiler uses
UPPER_SNAKE names.

```kotlin
@Suppress("GLOBAL_SCOPE_USAGE")
fun legacyEntryPoint() {
    GlobalScope.launch { doWork() }
}

@file:Suppress("RUN_BLOCKING_IN_SUSPEND")
package com.example.legacy
```

### Detekt

Use `@Suppress` with the **Detekt** rule id (PascalCase). You can also disable rules in
`detekt.yml` (see [detekt-rules README](../detekt-rules/README.md#suppressing-rules)).

```kotlin
@Suppress("BlockingCallInCoroutine")
suspend fun legitimateBlockingCall() {
    Thread.sleep(100)  // Documented special case
}
```

### Android Lint

Use `@SuppressLint` with the **Lint** issue id. Lint identifiers match Detekt/IntelliJ where the
same rule exists.

```kotlin
@SuppressLint("MainDispatcherMisuse")
fun onMainThread() {
    Thread.sleep(100)
}
```

### IntelliJ / Android Studio inspections

Use `@Suppress` with the **IntelliJ** short name (same as Detekt/Lint when the rule is shared). The
inspection will stop reporting on that element.

```kotlin
@Suppress("GlobalScopeUsage")
fun temporary() {
    GlobalScope.launch { }
}
```

---

## Same rule in multiple tools

If the same line is reported by more than one tool (e.g. Compiler and Detekt), list all applicable
identifiers in one `@Suppress`:

```kotlin
@Suppress("GLOBAL_SCOPE_USAGE", "GlobalScopeUsage")
fun sharedCode() {
    GlobalScope.launch { }
}
```

That way the code compiles, passes Detekt, and the IDE does not show the inspection for that call.

---

## References

- [Rule codes and practices](BEST_PRACTICES_COROUTINES.md#rule-codes-reference) — full list and
  links to each section
- [detekt-rules README](../detekt-rules/README.md#suppressing-rules) — Detekt configuration and
  excludes
- [lint-rules README](../lint-rules/README.md#suppressing-rules) — Lint configuration and scope
