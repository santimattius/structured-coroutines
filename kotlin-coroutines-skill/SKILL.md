---
name: kotlin-coroutines-skill
type: skill
description: 'Expert guidance on Kotlin Coroutines best practices, structured concurrency, and safe async code. Use when developers mention: (1) Kotlin Coroutines, suspend, launch, async, or Flow, (2) "use coroutines" or "structured concurrency" in Kotlin, (3) GlobalScope, viewModelScope, lifecycleScope, or CoroutineScope, (4) Dispatchers (Main, IO, Default, Unconfined), (5) cancellation, CancellationException, or SupervisorJob, (6) runBlocking, blocking in coroutines, or wrong dispatchers, (7) Channels, produce, or consumeEach, (8) testing coroutines, runTest, or TestDispatcher, (9) architecture layers with suspend/Flow vs callbacks.'
license: MIT
metadata:
  version: "1.0.0"
---

# Kotlin Coroutines

## Overview

This skill provides expert guidance on Kotlin Coroutines, covering structured concurrency, scopes,
Dispatchers, cancellation, exception handling, Channels, Flow, and testing. Use this skill to help
developers write safe, maintainable concurrent code aligned with Kotlin 1.9+ and Kotlin 2.0+
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
   documented case.
8. Never pass `Job()` or `SupervisorJob()` directly to builders (e.g. `launch(Job()) { }`). Use
   `supervisorScope { }` or a scope defined with `SupervisorJob()` for supervisor semantics.
9. Cancellation handling (apply all):
   - Never swallow `CancellationException`; rethrow it in catch blocks.
   - Do not use `CancellationException` for domain errors; use normal exceptions instead.
   - In long loops, add `yield()` or `ensureActive()`/`isActive` checks.
   - For suspend calls in `finally`, use `withContext(NonCancellable) { }`.
   - Do not reuse a scope after `scope.cancel()`; use `coroutineContext.job.cancelChildren()` to
     stop only children while keeping the scope alive.
10. In tests use `kotlinx-coroutines-test`: `runTest`, virtual time, `advanceTimeBy`,
    `advanceUntilIdle`, and inject `TestDispatcher`/`StandardTestDispatcher`; avoid real `delay()`
    with `runBlocking`.
11. Prefer `produce { }` for channels so they close when the coroutine ends. Do not share
    `consumeEach` across multiple consumers; use `for (x in channel)` per consumer.
12. When several practices apply (e.g. GlobalScope + wrong Dispatchers), use each relevant reference
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

| Topic / Error / Question                                                      | Reference file                                                                                                |
|-------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| **GlobalScope**, scope lifetime, "where should I launch?"                     | [ref-1-1-global-scope.md](references/ref-1-1-global-scope.md)                                                 |
| **async without await**, fire-and-forget with async                           | [ref-1-2-async-without-await.md](references/ref-1-2-async-without-await.md)                                   |
| **Breaking structured concurrency**, launching in external scope from suspend | [ref-1-3-breaking-structured-concurrency.md](references/ref-1-3-breaking-structured-concurrency.md)           |
| **coroutineScope { launch { } }** as last line, "wait vs don't wait"          | [ref-2-1-launch-last-line-coroutine-scope.md](references/ref-2-1-launch-last-line-coroutine-scope.md)         |
| **runBlocking** inside suspend, blocking in coroutines                        | [ref-2-2-runblocking-in-suspend.md](references/ref-2-2-runblocking-in-suspend.md)                             |
| **Blocking I/O on Default/Main**, wrong Dispatchers for I/O                   | [ref-3-1-blocking-wrong-dispatchers.md](references/ref-3-1-blocking-wrong-dispatchers.md)                     |
| **Dispatchers.Unconfined** in production                                      | [ref-3-2-dispatchers-unconfined.md](references/ref-3-2-dispatchers-unconfined.md)                             |
| **Job() / SupervisorJob()** passed to launch/async/withContext                | [ref-3-3-job-context-builders.md](references/ref-3-3-job-context-builders.md)                                 |
| **Cancellation in loops**, long loops not responding to cancel                | [ref-4-1-cancellation-intensive-loops.md](references/ref-4-1-cancellation-intensive-loops.md)                 |
| **Swallowing CancellationException**, catch Exception and cancel              | [ref-4-2-swallowing-cancellation-exception.md](references/ref-4-2-swallowing-cancellation-exception.md)       |
| **Suspend in finally**, cleanup that needs to suspend                         | [ref-4-3-suspend-cleanup-noncancellable.md](references/ref-4-3-suspend-cleanup-noncancellable.md)             |
| **Reusing scope after cancel()**, cancelChildren vs cancel                    | [ref-4-4-reusing-cancelled-scope.md](references/ref-4-4-reusing-cancelled-scope.md)                           |
| **SupervisorJob() in a single builder**                                       | [ref-5-1-supervisor-job-single-builder.md](references/ref-5-1-supervisor-job-single-builder.md)               |
| **CancellationException for domain errors** (e.g. UserNotFound)               | [ref-5-2-cancellation-exception-domain-errors.md](references/ref-5-2-cancellation-exception-domain-errors.md) |
| **Slow tests**, real delay() in tests                                         | [ref-6-1-slow-tests-real-delays.md](references/ref-6-1-slow-tests-real-delays.md)                             |
| **Uncontrolled fire-and-forget in tests**, can't wait in tests                | [ref-6-2-uncontrolled-fire-and-forget-tests.md](references/ref-6-2-uncontrolled-fire-and-forget-tests.md)     |
| **Channel not closed**, manual Channel without close()                        | [ref-7-1-channel-close.md](references/ref-7-1-channel-close.md)                                               |
| **consumeEach with multiple consumers**                                       | [ref-7-2-consume-each-multiple-consumers.md](references/ref-7-2-consume-each-multiple-consumers.md)           |
| **Architecture**, layers (Data/Domain/Presentation), suspend vs callbacks     | [ref-8-architecture-patterns.md](references/ref-8-architecture-patterns.md)                                   |
| **Flow**, `flowOn`, `shareIn`, `stateIn`, cold vs hot streams, collect        | [ref-8-architecture-patterns.md](references/ref-8-architecture-patterns.md)                                   |

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

## Reference Files

Load these files as needed for the specific topic:

- **`references/ref-1-1-global-scope.md`** – GlobalScope, scope lifetime, framework/injected/local scopes
- **`references/ref-1-2-async-without-await.md`** – async only when result is needed; use launch for
  fire-and-forget
- **`references/ref-1-3-breaking-structured-concurrency.md`** – Launching in external scope from suspend
- **`references/ref-2-1-launch-last-line-coroutine-scope.md`** – coroutineScope { launch { } } as last line
- **`references/ref-2-2-runblocking-in-suspend.md`** – runBlocking inside suspend, blocking in coroutines
- **`references/ref-3-1-blocking-wrong-dispatchers.md`** – Blocking I/O on Default/Main, correct Dispatchers
- **`references/ref-3-2-dispatchers-unconfined.md`** – Dispatchers.Unconfined in production
- **`references/ref-3-3-job-context-builders.md`** – Job()/SupervisorJob() passed to builders
- **`references/ref-4-1-cancellation-intensive-loops.md`** – Cancellation in loops, yield/ensureActive
- **`references/ref-4-2-swallowing-cancellation-exception.md`** – Never swallow CancellationException
- **`references/ref-4-3-suspend-cleanup-noncancellable.md`** – Suspend in finally, withContext(NonCancellable)
- **`references/ref-4-4-reusing-cancelled-scope.md`** – Reusing scope after cancel, cancelChildren
- **`references/ref-5-1-supervisor-job-single-builder.md`** – SupervisorJob in a single builder
- **`references/ref-5-2-cancellation-exception-domain-errors.md`** – CancellationException for domain errors
- **`references/ref-6-1-slow-tests-real-delays.md`** – Slow tests, real delay(), runTest/virtual time
- **`references/ref-6-2-uncontrolled-fire-and-forget-tests.md`** – Fire-and-forget in tests, controlled scope
- **`references/ref-7-1-channel-close.md`** – Channel close, produce vs manual Channel
- **`references/ref-7-2-consume-each-multiple-consumers.md`** – consumeEach vs for (x in channel) per consumer
- **`references/ref-8-architecture-patterns.md`** – Data/Domain/Presentation, suspend vs callbacks, Flow, testing

## Best Practices Summary

1. **Prefer structured concurrency** – Use `coroutineScope` + `async`/`launch` inside suspend; avoid
   launching in external scope from suspend unless documented.
2. **Use the right scope** – Framework scopes (viewModelScope, lifecycleScope) or injected scope;
   never GlobalScope in production.
3. **Use async only when you need a result** – Otherwise use `launch`.
4. **Use explicit Dispatchers** – IO for blocking I/O, Default for CPU, Main for UI; never block on
   Default/Main.
5. **Respect cancellation** – Rethrow CancellationException; use yield/ensureActive in long loops;
   use withContext(NonCancellable) for suspend cleanup in finally.
6. **Do not misuse CancellationException** – Use normal exceptions for domain errors.
7. **Test with virtual time** – runTest, TestDispatcher, advanceTimeBy; avoid real delay() in tests.
8. **Channels** – Prefer produce; if using Channel manually, document when close() is called; one
   consumer per channel with for (x in channel).

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
