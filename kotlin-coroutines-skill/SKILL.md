---
name: kotlin-coroutines-skill
description: "Expert guidance on Kotlin Coroutines and structured concurrency. Use when developers write or review async Kotlin code and need help ensuring correctness, safety, and testability of coroutine-based implementations."
license: MIT
metadata:
  version: "2.0.0"
---

# Kotlin Coroutines

## Overview

This skill provides expert guidance on Kotlin Coroutines, covering structured concurrency, scopes,
Dispatchers (including main-safe suspend and dispatcher injection), cancellation (including
`withTimeout` semantics), exception handling (`CoroutineExceptionHandler`, `launch` vs `async`),
Channels, Flow (cold vs hot, `collectLatest`, `SharedFlow` configuration, blocking in `flow {}`),
lifecycle-aware collection on Android, and testing (virtual time, `setMain`/`resetMain`). Use this
skill to help developers write safe, maintainable concurrent code aligned with Kotlin 1.9+/2.0+
conventions and official best practices.

## Agent Behavior Contract (Follow These Rules)

1. **Identify** the practice or error from the user's code or question (e.g. GlobalScope,
   runBlocking in suspend, swallowing CancellationException) and **open** the corresponding
   reference from the Triage table in `references/`.
2. **Apply** the strict rules below in every response. Do not suggest or leave code that violates
   them.
3. **Respond** in the required format: Analysis → Erroneous code → Optimized code → Explanation. If
   the user only asks a conceptual question (no code), skip erroneous/optimized snippets and focus
   on analysis and explanation.
4. Do not recommend `GlobalScope` in production. Use framework scopes (`viewModelScope`,
   `lifecycleScope`, `rememberCoroutineScope`), injected scopes, or local scopes (
   `coroutineScope { }`, `withContext { }`). If an external scope is required, justify and document
   it.
5. Use `async` only when a return value is needed; if `await()` is never called, use `launch`.
   Preserve structured concurrency: inside suspend functions use `coroutineScope { }` + `async`/
   `launch`; do not launch in an external scope from suspend unless work must outlive the flow, and
   then document it.
6. Never use `runBlocking` inside suspend functions or coroutine-based code. Avoid ending a suspend
   function with `coroutineScope { launch { } }` as the last line when the intent is fire-and-forget
   — `coroutineScope` waits for all children and blocks the caller; use an explicit external scope
   and document it if the work must truly run in the background beyond the caller's lifetime.
7. Use explicit Dispatchers: `Dispatchers.Default` for CPU-bound work, `Dispatchers.Main`/
   `Main.immediate` for UI, `withContext(Dispatchers.IO)` for blocking I/O. Never perform blocking
   I/O on Default or Main. Do not use `Dispatchers.Unconfined` in production unless for a rare,
   documented case. Make suspend functions **main-safe**: move blocking work into
   `withContext(Dispatchers.IO)` so callers on Main are never blocked. Inject `CoroutineDispatcher`
   as a constructor parameter (default to real dispatcher; replace with `TestDispatcher` in tests).
8. Never pass `Job()` or `SupervisorJob()` directly to builders (e.g. `launch(Job()) { }`). Use
   `supervisorScope { }` or a scope defined with `SupervisorJob()` for supervisor semantics. When
   running independent tasks with `awaitAll`, use `supervisorScope` instead of `coroutineScope` so
   one failure does not cancel sibling deferreds.
9. Cancellation handling (apply all):
   - Never swallow `CancellationException`; rethrow it in catch blocks.
   - Do not use `CancellationException` for domain errors; use normal exceptions instead.
   - In long loops and repeating/polling work, add `yield()`, `ensureActive()`, or `while (isActive)`
     with `delay(interval)` so the coroutine responds to cancellation.
   - For suspend calls in `finally`, use `withContext(NonCancellable) { }`.
   - Do not reuse a scope after `scope.cancel()`; use `coroutineContext.job.cancelChildren()` to
     stop only children while keeping the scope alive.
   - Prefer `withTimeoutOrNull` over `withTimeout` to avoid unintentionally cancelling the parent
     scope. If using `withTimeout`, catch `TimeoutCancellationException` explicitly. Always ensure
     resources opened inside `withTimeout` are cleaned up in `finally`.
10. Exception handling:
    - Uncaught exceptions in `launch` propagate to `CoroutineExceptionHandler`; in `async`, the
      exception is stored in the `Deferred` and only thrown on `await()`. Always call `await()` on
      `async` blocks to avoid silently losing exceptions.
    - Use `CoroutineExceptionHandler` at scope level for `launch` uncaught exceptions.
11. In tests use `kotlinx-coroutines-test`: `runTest`, virtual time, `advanceTimeBy`,
    `advanceUntilIdle`, and inject `TestDispatcher`/`StandardTestDispatcher`; avoid real `delay()`
    with `runBlocking`. Replace `Dispatchers.Main` using `Dispatchers.setMain(TestDispatcher())` in
    `@Before` and `Dispatchers.resetMain()` in `@After`.
12. Prefer `produce { }` for channels so they close when the coroutine ends. Do not share
    `consumeEach` across multiple consumers; use `for (x in channel)` per consumer.
13. Flow best practices:
    - Keep `flow { }` builder non-blocking; use `flowOn(Dispatchers.IO)` or suspend APIs.
    - Use `StateFlow` for shared UI state (replays last value); use `SharedFlow` for events with
      explicit `replay`, `extraBufferCapacity`, and `onBufferOverflow` configuration.
    - Use `collectLatest` only when cancelling in-progress work is intentional (e.g. search); use
      `collect` when each item must be processed to completion.
    - On Android, collect flows with `repeatOnLifecycle(Lifecycle.State.STARTED)` or
      `flowWithLifecycle` to stop collection when the UI goes to background.
14. When several practices apply (e.g. GlobalScope + wrong Dispatchers), use each relevant reference
    and combine the fixes in one optimized snippet.

## Recommended Tools for Analysis

When analyzing Kotlin projects for coroutine issues:

1. **Project settings**
    - Use `Read` on `build.gradle.kts` / `build.gradle` for Kotlin version, `kotlinx-coroutines-*`
      dependencies, and `kotlinx-coroutines-test`.
    - Use `Grep` for `CoroutineScope`, `GlobalScope`, `runBlocking`, `Dispatchers`,
      `viewModelScope`, `lifecycleScope` to locate usage patterns.
2. **Scope and lifecycle**
    - Identify whether code runs on Android (viewModelScope, lifecycleScope), KMP, or plain JVM to
      recommend the right scope.

## Triage-First Playbook (Topic / Error → Reference)

| Topic / Error / Question                                                                      | Reference file                                                                                                            |
|-----------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| **GlobalScope**, scope lifetime, "where should I launch?"                                     | [ref-1-1-global-scope.md](references/ref-1-1-global-scope.md)                                                             |
| **async without await**, fire-and-forget with async                                           | [ref-1-2-async-without-await.md](references/ref-1-2-async-without-await.md)                                               |
| **Breaking structured concurrency**, launching in external scope from suspend                 | [ref-1-3-breaking-structured-concurrency.md](references/ref-1-3-breaking-structured-concurrency.md)                       |
| **awaitAll exception propagation**, coroutineScope vs supervisorScope for parallel tasks      | [ref-1-4-awaitall-exception-propagation.md](references/ref-1-4-awaitall-exception-propagation.md)                         |
| **coroutineScope { launch { } }** as last line, "wait vs don't wait"                          | [ref-2-1-launch-last-line-coroutine-scope.md](references/ref-2-1-launch-last-line-coroutine-scope.md)                     |
| **runBlocking** inside suspend, blocking in coroutines                                        | [ref-2-2-runblocking-in-suspend.md](references/ref-2-2-runblocking-in-suspend.md)                                         |
| **Blocking I/O on Default/Main**, wrong Dispatchers for I/O                                   | [ref-3-1-blocking-wrong-dispatchers.md](references/ref-3-1-blocking-wrong-dispatchers.md)                                 |
| **Main-safe suspend functions**, suspend blocking caller thread, ANR                         | [ref-3-2-main-safe-suspend.md](references/ref-3-2-main-safe-suspend.md)                                                   |
| **Dispatchers.Unconfined** in production                                                      | [ref-3-2-dispatchers-unconfined.md](references/ref-3-2-dispatchers-unconfined.md)                                         |
| **Job() / SupervisorJob()** passed to launch/async/withContext                                | [ref-3-3-job-context-builders.md](references/ref-3-3-job-context-builders.md)                                             |
| **Injecting Dispatchers**, hardcoded dispatcher, flaky tests, testability                     | [ref-3-5-inject-dispatchers.md](references/ref-3-5-inject-dispatchers.md)                                                 |
| **Cancellation in loops**, long loops not responding to cancel                                | [ref-4-1-cancellation-intensive-loops.md](references/ref-4-1-cancellation-intensive-loops.md)                             |
| **Periodic / repeating work**, polling, zombie coroutine, infinite loop without isActive      | [ref-4-2-periodic-repeating-work.md](references/ref-4-2-periodic-repeating-work.md)                                       |
| **Swallowing CancellationException**, catch Exception and cancel                              | [ref-4-2-swallowing-cancellation-exception.md](references/ref-4-2-swallowing-cancellation-exception.md)                   |
| **Suspend in finally**, cleanup that needs to suspend                                         | [ref-4-3-suspend-cleanup-noncancellable.md](references/ref-4-3-suspend-cleanup-noncancellable.md)                         |
| **Reusing scope after cancel()**, cancelChildren vs cancel                                    | [ref-4-4-reusing-cancelled-scope.md](references/ref-4-4-reusing-cancelled-scope.md)                                       |
| **withTimeout scope cancellation**, TimeoutCancellationException, withTimeoutOrNull           | [ref-4-6-withtimeout-scope-cancellation.md](references/ref-4-6-withtimeout-scope-cancellation.md)                         |
| **withTimeout resource cleanup**, resource leak on timeout, finally, NonCancellable           | [ref-4-7-withtimeout-resource-cleanup.md](references/ref-4-7-withtimeout-resource-cleanup.md)                             |
| **SupervisorJob() in a single builder**                                                       | [ref-5-1-supervisor-job-single-builder.md](references/ref-5-1-supervisor-job-single-builder.md)                           |
| **CancellationException for domain errors** (e.g. UserNotFound)                               | [ref-5-2-cancellation-exception-domain-errors.md](references/ref-5-2-cancellation-exception-domain-errors.md)             |
| **CoroutineExceptionHandler vs async**, exceptions in async stored in Deferred until await() | [ref-5-3-exception-handler-async.md](references/ref-5-3-exception-handler-async.md)                                       |
| **Slow tests**, real delay() in tests                                                         | [ref-6-1-slow-tests-real-delays.md](references/ref-6-1-slow-tests-real-delays.md)                                         |
| **Uncontrolled fire-and-forget in tests**, can't wait in tests                                | [ref-6-2-uncontrolled-fire-and-forget-tests.md](references/ref-6-2-uncontrolled-fire-and-forget-tests.md)                 |
| **Dispatchers.Main in tests**, setMain/resetMain, CI flaky tests                             | [ref-6-3-setmain-resetmain.md](references/ref-6-3-setmain-resetmain.md)                                                   |
| **Channel not closed**, manual Channel without close()                                        | [ref-7-1-channel-close.md](references/ref-7-1-channel-close.md)                                                           |
| **consumeEach with multiple consumers**                                                       | [ref-7-2-consume-each-multiple-consumers.md](references/ref-7-2-consume-each-multiple-consumers.md)                       |
| **Architecture**, layers (Data/Domain/Presentation), suspend vs callbacks                    | [ref-8-architecture-patterns.md](references/ref-8-architecture-patterns.md)                                               |
| **Lifecycle-aware Flow collection** (Android), repeatOnLifecycle, flowWithLifecycle           | [ref-8-2-lifecycle-aware-flow.md](references/ref-8-2-lifecycle-aware-flow.md)                                             |
| **Blocking in flow { }**, Thread.sleep in flow, flowOn                                       | [ref-9-1-flow-blocking-call.md](references/ref-9-1-flow-blocking-call.md)                                                 |
| **Cold vs hot flows**, StateFlow, SharedFlow, shareIn, stateIn, collect                       | [ref-9-2-cold-vs-hot-flows.md](references/ref-9-2-cold-vs-hot-flows.md)                                                   |
| **collectLatest semantics**, cancels previous block, search vs complete work                  | [ref-9-3-collect-latest.md](references/ref-9-3-collect-latest.md)                                                         |
| **SharedFlow configuration**, replay, extraBufferCapacity, onBufferOverflow, backpressure     | [ref-9-4-shared-flow-configuration.md](references/ref-9-4-shared-flow-configuration.md)                                   |

## Core Patterns Reference

### When to Use Each Coroutine Tool

**launch** – Fire-and-forget or UI-triggered work that does not return a value

```kotlin
// Use for: Work that does not need a result; lifecycle-bound to scope
viewModelScope.launch {
    updateUI(loadData())
}
```

**async / await** – Parallel work when you need a return value

```kotlin
// Use for: Deferred result; always await (or awaitAll) to preserve structure
coroutineScope {
    val a = async { fetchA() }
    val b = async { fetchB() }
    combine(a.await(), b.await())
}
```

**coroutineScope** – Structured child work inside a suspend function

```kotlin
// Use for: Subtasks that must complete or cancel with the current scope
suspend fun loadAll() = coroutineScope {
    val one = async { loadOne() }
    val two = async { loadTwo() }
    Pair(one.await(), two.await())
}
```

**withContext** – Switch dispatcher or run cleanup (e.g. NonCancellable)

```kotlin
// Use for: Blocking I/O off Main/Default; cleanup in finally
withContext(Dispatchers.IO) { readFile(path) }
withContext(NonCancellable) { db.close() }
```

**supervisorScope** – Children do not cancel each other on failure

```kotlin
// Use for: Independent child jobs (e.g. multiple UI updates)
supervisorScope {
    launch { updateA() }
    launch { updateB() }
}
```

**produce** – Channel that closes when the coroutine ends

```kotlin
// Use for: Single producer; automatic close when scope completes
fun CoroutineScope.flowFromApi() = produce {
    for (item in api.stream()) send(item)
}
```

### Common Scenarios

**Scenario: Single network request with UI update**

```kotlin
viewModelScope.launch {
    val data = withContext(Dispatchers.IO) { repo.fetch() }
    updateUI(data)
}
```

**Scenario: Multiple parallel requests**

```kotlin
coroutineScope {
    val users = async { repo.getUsers() }
    val posts = async { repo.getPosts() }
    show(users.await(), posts.await())
}
```

**Scenario: Cancellation-friendly loop**

```kotlin
for (i in list) {
    yield() // or ensureActive()
    process(i)
}
```

## Best Practices Summary

1. **Prefer structured concurrency** – Use `coroutineScope` + `async`/`launch` inside suspend; avoid
   launching in external scope from suspend unless documented. For independent tasks, use
   `supervisorScope` + `awaitAll` so one failure does not cancel siblings.
2. **Use the right scope** – Framework scopes (viewModelScope, lifecycleScope) or injected scope;
   never GlobalScope in production.
3. **Use async only when you need a result** – Otherwise use `launch`. Always call `await()` on
   every `Deferred`; exceptions are only thrown at `await()`, not caught by `CoroutineExceptionHandler`.
4. **Use explicit, injected Dispatchers** – IO for blocking I/O, Default for CPU, Main for UI; never
   block on Default/Main. Make suspend functions main-safe (`withContext(IO)` internally). Inject
   dispatchers for testability.
5. **Respect cancellation** – Rethrow CancellationException; use `while(isActive)` + `delay` for
   repeating work; use `yield`/`ensureActive` in long loops; use `withContext(NonCancellable)` for
   suspend cleanup in `finally`. Prefer `withTimeoutOrNull`; ensure resources are cleaned up on
   timeout.
6. **Do not misuse CancellationException** – Use normal exceptions for domain errors.
7. **Test with virtual time** – `runTest`, `TestDispatcher`, `advanceTimeBy`; avoid real `delay()`
   in tests. Replace `Dispatchers.Main` with `setMain`/`resetMain` for reliable CI tests.
8. **Channels** – Prefer `produce`; if using Channel manually, document when `close()` is called;
   one consumer per channel with `for (x in channel)`.
9. **Flow** – Keep `flow { }` non-blocking (use `flowOn`); use `StateFlow` for state, `SharedFlow`
   for events with explicit buffer/overflow config; use `collectLatest` only when cancelling
   in-progress work is intentional.
10. **Android lifecycle** – Collect flows with `repeatOnLifecycle(STARTED)` or `flowWithLifecycle`
    so collection stops when UI is in background.

## Output Format (Required for Code Review / Refactor)

Structure every code-review or refactor response as:

1. **Problem Analysis** – Short description of what is wrong (e.g. scope lifetime, dispatcher,
   exception handling) and the risk (leaks, ANRs, flaky tests).
2. **Erroneous Code** – The original or problematic code snippet (clearly labeled).
3. **Optimized Code** – Refactored code that follows the guidelines (structured concurrency,
   correct scopes, Dispatchers, exception/cancellation handling).
4. **Technical Explanation** – Why the optimized version is safer or more correct: lifecycle,
   cancellation propagation, thread usage, testability.

For conceptual-only questions, skip erroneous/optimized snippets and keep analysis and explanation.

## Verification Checklist (When You Change Coroutine Code)

- Confirm which scope is in use (framework, injected, or local) and that it matches lifecycle
  expectations.
- After refactors: run tests, especially those that use coroutines or virtual time (see ref-6-*).
- If touching cancellation or cleanup: ensure CancellationException is rethrown and suspend cleanup
  uses NonCancellable where needed.
- If touching Dispatchers: ensure no blocking I/O on Default or Main.
