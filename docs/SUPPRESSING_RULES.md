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
| TEST_004     | 6.4 | —                                                         | `RunBlockingInsteadOfRunTest`                 | `RunBlockingInsteadOfRunTest`                | `RunBlockingInsteadOfRunTest`                |
| ARCH_002     | 8.2 | —                                                         | —                                             | `LifecycleAwareScope`, `ViewModelScopeLeak`, `LifecycleAwareFlowCollection` | `LifecycleAwareFlowCollection`                |
| COMPOSE_001  | 8.3 | —                                                         | —                                             | `CollectAsStateWithoutLifecycle`             | `CollectAsStateWithoutLifecycle`              |
| FLOW_001     | 9.1  | —                                                         | `FlowBlockingCall`                            | `FlowBlockingCall`                           | —                                            |
| FLOW_005     | 9.6  | —                                                         | `MissingCatchInFlow`                          | `MissingCatchInFlow`                         | `MissingCatchInFlow`                         |
| FLOW_010     | 9.5  | —                                                         | `MutableFlowExposed`                          | —                                            | `MutableFlowExposed`                         |
| CONCUR_003   | 1.5  | —                                                         | `SequentialAsyncAwait`                        | —                                            | `SequentialAsyncAwait`                       |
| INTEROP_001  | 10.1 | `SUSPEND_COROUTINE_WITHOUT_CANCELLATION` (Compiler only; cannot suppress FIR this way — use Detekt/IDE instead) | `SuspendCoroutineWithoutCancellation` | —        | `SuspendCoroutineWithoutCancellation`        |
| INTEROP_002  | 10.2 | `CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE` (Compiler only; cannot suppress FIR this way — use Detekt/IDE instead)     | `CallbackFlowWithoutAwaitClose`       | —        | `CallbackFlowWithoutAwaitClose`              |
| KMP_001      | 11.1 | —                                                         | `DispatchersIOInCommonMain`                  | `DispatchersIOInCommonMain`                   | `DispatchersIOInCommonMain`                   |
| CONCUR_001   | 12.1 | —                                                         | `SynchronizedInCoroutine`                      | `SynchronizedInCoroutine`                     | `SynchronizedInCoroutine`                     |
| CONCUR_002   | 12.2 | —                                                         | `SharedMutableStateInCoroutine`              | —                                             | —                                             |
| CONCUR_004   | 3.6  | —                                                         | `RedundantWithContext`                       | —                                             | `RedundantWithContext`                        |
| FLOW_006     | 9.7  | —                                                         | `StateInWithEagerlyStrategy`                 | `StateInWithEagerlyStrategy`                  | `StateInWithEagerlyStrategy`                  |
| FLOW_007     | 9.8  | —                                                         | —                                            | `LaunchInWithUnstructuredScope`               | `LaunchInWithUnstructuredScope`               |
| FLOW_008     | 9.9  | —                                                         | `SideEffectInMapOperator`                    | —                                             | `SideEffectInMapOperator`                     |
| KMP_002      | 11.2 | —                                                         | `RunBlockingInCommonMain`                    | `RunBlockingInCommonMain`                     | —                                             |
| KMP_003      | 11.3 | —                                                         | `MainScopeWithoutCancel`                     | —                                             | —                                             |
| BACKEND_001  | 13.1 | —                                                         | `BlockingCallInCoroutineBackend`             | —                                             | —                                             |
| BACKEND_002  | 3.7  | —                                                         | `ThreadLocalNotPropagated`                   | —                                             | —                                             |
| TEST_005     | 6.5  | —                                                         | `HardcodedDispatcherInClass`               | —                                             | `HardcodedDispatcherInClass`                  |
| TEST_006     | 6.6  | —                                                         | —                                            | —                                             | `CoroutineNotCompletedInTest`                 |
| COMPOSE_002  | 8.4  | —                                                         | —                                            | `RememberScopeForInit`                       | `RememberScopeForInit`                        |
| COMPOSE_003  | 8.5  | —                                                         | —                                            | `SideEffectInComposable`                     | —                                             |
| FLOW_009     | 9.10 | —                                                         | —                                            | —                                             | `FlatMapOperatorChoice`                       |
| FLOW_011     | 9.11 | —                                                         | `SharedFlowForOneshotEvents`                 | —                                             | `SharedFlowForOneshotEvents`                  |
| INTEROP_003  | 10.3 | —                                                         | `ChannelFlowVsCallbackFlow`                  | —                                             | `ChannelFlowVsCallbackFlow`                   |
| INTEROP_004  | 10.4 | —                                                         | `BlockingFutureGet`                          | `BlockingFutureGet`                          | `BlockingFutureGet`                           |
| DEBUG_001    | 14.1 | —                                                         | `MissingCoroutineName`                       | —                                             | —                                             |

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
