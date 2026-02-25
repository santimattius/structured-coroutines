# Kotlin Coroutines Best Practices

A comprehensive guide to good and bad practices when working with Kotlin Coroutines, structured
concurrency, and Flow. Each practice has a **rule code** (e.g. `SCOPE_001`) for direct reference in
tools and messages; links can point to the anchor of the corresponding section.

## Table of Contents

- [Rule codes reference](#rule-codes-reference)
- [1. Asynchronous Builders and Scopes](#1-asynchronous-builders-and-scopes)
- [2. Blocking Builders and runBlocking](#2-blocking-builders-and-runblocking)
- [3. Context and Dispatchers](#3-context-and-dispatchers)
- [4. Job, Lifecycle, and Cancellation](#4-job-lifecycle-and-cancellation)
- [5. Exceptions and SupervisorJob](#5-exceptions-and-supervisorjob)
- [6. Testing Coroutines](#6-testing-coroutines)
- [7. Channels and Actors](#7-channels-and-actors)
- [8. Architecture Patterns](#8-architecture-patterns)
- [9. Flow](#9-flow)
- [Quick Reference Checklist](#quick-reference-checklist)
- [Tool Implementation Matrix](#tool-implementation-matrix)

---

## Rule codes reference

Use these codes in compiler/IDE messages and in links so users can jump directly to the practice.
Link format: `BEST_PRACTICES_COROUTINES.md#<anchor>` (anchor = section heading in lowercase,
hyphens, e.g. `#11-using-globalscope-in-production-code`).

| Code           | §   | Practice                                                                                                             |
|----------------|-----|----------------------------------------------------------------------------------------------------------------------|
| `SCOPE_001`    | 1.1 | [Using GlobalScope in Production Code](#11-using-globalscope-in-production-code)                                     |
| `SCOPE_002`    | 1.2 | [Using async Without Calling await](#12-using-async-without-calling-await)                                           |
| `SCOPE_003`    | 1.3 | [Breaking Structured Concurrency](#13-breaking-structured-concurrency)                                               |
| `SCOPE_004`    | 1.4 | [awaitAll and Exception Propagation](#14-awaitall-and-exception-propagation)                                         |
| `RUNBLOCK_001` | 2.1 | [Using launch on the Last Line of coroutineScope](#21-using-launch-on-the-last-line-of-coroutinescope)               |
| `RUNBLOCK_002` | 2.2 | [Using runBlocking Inside Suspend Functions](#22-using-runblocking-inside-suspend-functions)                         |
| `DISPATCH_001` | 3.1 | [Mixing Blocking Code with Wrong Dispatchers](#31-mixing-blocking-code-with-wrong-dispatchers)                       |
| `DISPATCH_002` | 3.2 | [Main-Safe Suspend Functions](#32-main-safe-suspend-functions)                                                       |
| `DISPATCH_003` | 3.3 | [Abusing Dispatchers.Unconfined](#33-abusing-dispatchersunconfined)                                                  |
| `DISPATCH_004` | 3.4 | [Passing Job() Directly as Context to Builders](#34-passing-job-directly-as-context-to-builders)                     |
| `DISPATCH_005` | 3.5 | [Injecting Dispatchers for Testability](#35-injecting-dispatchers-for-testability)                                   |
| `CANCEL_001`   | 4.1 | [Ignoring Cancellation in Intensive Loops](#41-ignoring-cancellation-in-intensive-loops)                             |
| `CANCEL_002`   | 4.2 | [Periodic or Repeating Work](#42-periodic-or-repeating-work)                                                         |
| `CANCEL_003`   | 4.3 | [Swallowing CancellationException](#43-swallowing-cancellationexception)                                             |
| `CANCEL_004`   | 4.4 | [Suspendable Cleanup Without NonCancellable](#44-suspendable-cleanup-without-noncancellable)                         |
| `CANCEL_005`   | 4.5 | [Reusing a Cancelled CoroutineScope](#45-reusing-a-cancelled-coroutinescope)                                         |
| `CANCEL_006`   | 4.6 | [withTimeout and Scope Cancellation](#46-withtimeout-and-scope-cancellation)                                         |
| `CANCEL_007`   | 4.7 | [withTimeout and Resource Cleanup](#47-withtimeout-and-resource-cleanup)                                             |
| `EXCEPT_001`   | 5.1 | [Using SupervisorJob as an Argument in a Single Builder](#51-using-supervisorjob-as-an-argument-in-a-single-builder) |
| `EXCEPT_002`   | 5.2 | [Extending CancellationException for Domain Errors](#52-extending-cancellationexception-for-domain-errors)           |
| `EXCEPT_003`   | 5.3 | [CoroutineExceptionHandler and launch vs async](#53-coroutineexceptionhandler-and-launch-vs-async)                   |
| `TEST_001`     | 6.1 | [Slow Tests with Real Delays](#61-slow-tests-with-real-delays)                                                       |
| `TEST_002`     | 6.2 | [Uncontrolled Fire-and-Forget Coroutines in Tests](#62-uncontrolled-fire-and-forget-coroutines-in-tests)             |
| `TEST_003`     | 6.3 | [Replacing Dispatchers.Main in Tests](#63-replacing-dispatchersmain-in-tests)                                        |
| `CHANNEL_001`  | 7.1 | [Forgetting to Close Manual Channels](#71-forgetting-to-close-manual-channels)                                       |
| `CHANNEL_002`  | 7.2 | [Sharing consumeEach Among Multiple Consumers](#72-sharing-consumeeach-among-multiple-consumers)                     |
| `ARCH_001`     | 8.1 | [General Recommendations](#81-general-recommendations)                                                               |
| `ARCH_002`     | 8.2 | [Lifecycle-Aware Flow Collection (Android)](#82-lifecycle-aware-flow-collection-android)                             |
| `FLOW_001`     | 9.1 | [Blocking Code in flow { } Builder](#91-blocking-code-in-flow--builder)                                              |
| `FLOW_002`     | 9.2 | [Cold vs Hot Flows (StateFlow / SharedFlow)](#92-cold-vs-hot-flows-stateflow--sharedflow)                            |
| `FLOW_003`     | 9.3 | [collectLatest Cancels Previous Work](#93-collectlatest-cancels-previous-work)                                       |
| `FLOW_004`     | 9.4 | [SharedFlow Configuration](#94-sharedflow-configuration)                                                             |

**Convention:** prefix by category (SCOPE, RUNBLOCK, DISPATCH, CANCEL, EXCEPT, TEST, CHANNEL, ARCH,
FLOW) + 3-digit number. Use the code in diagnostic messages and link to this document with the
anchor above (e.g.
`[SCOPE_001] GlobalScope usage... Ver: docs/BEST_PRACTICES_COROUTINES.md#11-using-globalscope-in-production-code`).

---

## 1. Asynchronous Builders and Scopes

### 1.1 SCOPE_001 — Using GlobalScope in Production Code

|                  | Description                                                                                                                                                                                                                                                                                                                                                                           |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Launching coroutines in `GlobalScope` (e.g., `GlobalScope.launch { }` or `GlobalScope.async { }`). This breaks the coroutine tree, preventing proper cancellation and exception propagation, making it difficult to reason about job lifetimes.                                                                                                                                       |
| **Recommended**  | Always use a meaningful `CoroutineScope`: framework scopes (`viewModelScope`, `lifecycleScope`, `rememberCoroutineScope`), custom injected scopes (`applicationScope`, `backgroundScope`), or local scopes in suspend functions (`coroutineScope { }`, `withContext { }`). Only launch independent processes in an external scope when absolutely necessary, and document it clearly. |

### 1.2 SCOPE_002 — Using async Without Calling await

|                  | Description                                                                                                                                                                                   |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Using `async` just to launch work without consuming the `Deferred` (e.g., `scope.async { doWork() }` without `await()`). This confuses readers and can hide exceptions inside the `Deferred`. |
| **Recommended**  | Use `launch` for fire-and-forget work and `async` only when you need a return value. Simple rule: if you never call `await`, you should use `launch`.                                         |

### 1.3 SCOPE_003 — Breaking Structured Concurrency

|                  | Description                                                                                                                                                                                                                                                                     |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Inside a suspend function or `coroutineScope`, launching work in an external scope without justification (e.g., `backgroundScope.launch { }`). This means work won't be cancelled with the caller, complicating resource cleanup, testing, and traceability.                    |
| **Recommended**  | Respect structured concurrency by default. Inside suspend functions, create subtasks with `coroutineScope { }` + `async`/`launch`. Only use external scopes for truly background processes that must survive the current flow (e.g., offline analytics, deferred cache writes). |

### 1.4 SCOPE_004 — awaitAll and Exception Propagation

|                  | Description                                                                                                                                                                                     |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Assuming that `coroutineScope { awaitAll(deferred1, deferred2, ...) }` runs tasks independently. The first exception cancels all other deferreds (structured concurrency).                      |
| **Recommended**  | Use `supervisorScope { awaitAll(...) }` and handle each `Deferred`'s exception separately when you need independent failure semantics. In `coroutineScope`, the first failure cancels the rest. |

---

## 2. Blocking Builders and runBlocking

### 2.1 RUNBLOCK_001 — Using launch on the Last Line of coroutineScope

|                  | Description                                                                                                                                                                                                                                      |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Ending a suspend function with `coroutineScope { launch { } }`. This appears to be fire-and-forget, but `coroutineScope` still waits for all children to complete.                                                                               |
| **Recommended**  | If you want the function to wait, execute the body directly inside `coroutineScope` without wrapping in `launch`. If you truly don't want to wait, launch in an explicit external scope to make it clear you're breaking structured concurrency. |

### 2.2 RUNBLOCK_002 — Using runBlocking Inside Suspend Functions

|                  | Description                                                                                                                                                                                                                                                                |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Calling `runBlocking` from coroutine-based code, especially inside suspend functions. This blocks the current thread, breaks the non-blocking model, and can cause deadlocks or ANRs on Android.                                                                           |
| **Recommended**  | Use `runBlocking` only as a bridge from purely blocking code to coroutines (legacy entry points, console scripts, interop with blocking APIs). Inside suspend functions, use suspend versions of libraries or wrap blocking operations with `withContext(Dispatchers.IO)`. |

---

## 3. Context and Dispatchers

### 3.1 DISPATCH_001 — Mixing Blocking Code with Wrong Dispatchers

|                  | Description                                                                                                                                                                                                                           |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Performing blocking I/O (files, JDBC, synchronous libraries) on `Dispatchers.Default` or `Dispatchers.Main`. This can freeze the UI or exhaust the thread pool through prolonged blocking.                                            |
| **Recommended**  | Use `Dispatchers.Default` for CPU-bound work, `Dispatchers.Main.immediate` for UI, and `withContext(Dispatchers.IO)` or a limited parallelism dispatcher for blocking operations. Prefer suspend APIs over wrapping synchronous APIs. |

### 3.2 DISPATCH_002 — Main-Safe Suspend Functions

|                  | Description                                                                                                                                                                                                    |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Exposing suspend functions that perform blocking work on the calling thread. Callers may invoke them from the main thread, causing ANRs or freezes.                                                            |
| **Recommended**  | Suspend functions should be **main-safe**: safe to call from the main thread. Move blocking work inside `withContext(Dispatchers.IO)` (or the appropriate dispatcher) so the function never blocks the caller. |

### 3.3 DISPATCH_003 — Abusing Dispatchers.Unconfined

|                  | Description                                                                                                                                                                                             |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Using `Dispatchers.Unconfined` in production to avoid thread switches. Code runs on whatever thread resumes it, making execution unpredictable and potentially running blocking calls on the UI thread. |
| **Recommended**  | Reserve `Dispatchers.Unconfined` for special cases or legacy testing. In production, always choose an appropriate dispatcher: `Default`, `Main`, `IO`, single-thread, or Loom.                          |

### 3.4 DISPATCH_004 — Passing Job() Directly as Context to Builders

|                  | Description                                                                                                                                                                                                                              |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Using `launch(Job()) { }` or `withContext(SupervisorJob()) { }` to control errors. This breaks the parent-child relationship and structured concurrency: the new Job becomes an independent parent.                                      |
| **Recommended**  | Let builders use the Job from the current scope to maintain the coroutine tree. For supervisor semantics, use `supervisorScope { }` inside suspend functions or a well-defined `CoroutineScope(SupervisorJob() + dispatcher + handler)`. |

### 3.5 DISPATCH_005 — Injecting Dispatchers for Testability

|                  | Description                                                                                                                                                                                                                      |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Hardcoding `Dispatchers.Main`, `Dispatchers.IO`, or `Dispatchers.Default` inside classes or suspend functions, making tests dependent on real threads and timing.                                                                |
| **Recommended**  | Inject `CoroutineDispatcher` (e.g. `IoDispatcher`, `MainDispatcher`) with sensible production defaults. In tests, replace with `StandardTestDispatcher` or `UnconfinedTestDispatcher` for deterministic, virtual-time execution. |

---

## 4. Job, Lifecycle, and Cancellation

### 4.1 CANCEL_001 — Ignoring Cancellation in Intensive Loops

|                  | Description                                                                                                                                                                                                                     |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Long loops or heavy computation without suspension points or cancellation checks. The coroutine won't respond to cancellation until calculation finishes or a delay occurs.                                                     |
| **Recommended**  | Insert cooperation points: `yield()` periodically in CPU-intensive suspend functions, or check `coroutineContext.isActive` / `ensureActive()` in loops. For blocking code, use an appropriate dispatcher with cancellable APIs. |

### 4.2 CANCEL_002 — Periodic or Repeating Work

|                  | Description                                                                                                                                                                              |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Implementing polling or periodic tasks with infinite loops that lack suspension or `isActive` checks. The coroutine becomes a "zombie" that keeps running after the scope is cancelled.  |
| **Recommended**  | Use a cancellation-cooperative pattern: `while (isActive)` with `ensureActive()` or `yield()` and `delay(interval)` inside the loop. This allows the scope to cancel the repeating task. |

### 4.3 CANCEL_003 — Swallowing CancellationException

|                  | Description                                                                                                                                                                                                 |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Catching `Exception` and treating `CancellationException` like any other error. This prevents cancellation from propagating correctly and can leave coroutines alive when they should terminate.            |
| **Recommended**  | Treat `CancellationException` separately: `catch (e: CancellationException) { throw e }` before catching other exceptions. Alternative: call `ensureActive()` inside catch blocks to re-throw if cancelled. |

### 4.4 CANCEL_004 — Suspendable Cleanup Without NonCancellable

|                  | Description                                                                                                                                                                                                                           |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Making suspend calls in `finally` blocks (DB writes, closing remote sessions) without `withContext(NonCancellable)`. If the coroutine is cancelling, any suspension throws `CancellationException` again and cleanup may not execute. |
| **Recommended**  | For critical cleanup that needs to suspend, use `withContext(NonCancellable) { }` inside `finally`.                                                                                                                                   |

### 4.5 CANCEL_005 — Reusing a Cancelled CoroutineScope

|                  | Description                                                                                                                                                           |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Calling `scope.cancel()` and then trying to launch more coroutines in that scope. A cancelled Job doesn't accept new children, and subsequent launches fail silently. |
| **Recommended**  | To clean up children but keep the scope usable, use `coroutineContext.job.cancelChildren()`. Only cancel the Job completely when the scope will no longer be reused.  |

### 4.6 CANCEL_006 — withTimeout and Scope Cancellation

|                  | Description                                                                                                                                                                                                  |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Using `withTimeout` without handling `TimeoutCancellationException`. Uncaught, it cancels the **parent scope** (not just the timeout block), so sibling coroutines can be cancelled unexpectedly.            |
| **Recommended**  | Prefer `withTimeoutOrNull` when you want "timeout → null" without affecting the scope. If using `withTimeout`, catch `TimeoutCancellationException` explicitly so it doesn't propagate and cancel the scope. |

### 4.7 CANCEL_007 — withTimeout and Resource Cleanup

|                  | Description                                                                                                                                                                                          |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Opening resources (files, connections) inside a `withTimeout` block without ensuring cleanup when the timeout fires asynchronously. The timeout can interrupt at any time, leaving resources leaked. |
| **Recommended**  | Ensure cleanup in `finally` or with `withContext(NonCancellable) { }` when the block is interrupted by timeout. Design so that resource lifecycle is well-defined on both success and timeout paths. |

---

## 5. Exceptions and SupervisorJob

### 5.1 EXCEPT_001 — Using SupervisorJob as an Argument in a Single Builder

|                  | Description                                                                                                                                                                                                            |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Passing `SupervisorJob()` to protect a single coroutine: `launch(SupervisorJob()) { }`. The SupervisorJob becomes the parent, but the real parent still uses a normal Job which gets cancelled on exceptions.          |
| **Recommended**  | Use SupervisorJob at the scope level: `CoroutineScope(SupervisorJob() + dispatcher + handler)` for independent children, or `supervisorScope { }` when children shouldn't cancel each other inside a suspend function. |

### 5.2 EXCEPT_002 — Extending CancellationException for Domain Errors

|                  | Description                                                                                                                                                                                                                              |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Defining domain errors that inherit from `CancellationException` (e.g., `class UserNotFoundException : CancellationException()`). These exceptions don't propagate upward normally; they only cancel the current coroutine and children. |
| **Recommended**  | Use normal `Exception` or `RuntimeException` for domain errors. Reserve `CancellationException` for true cancellation cases only.                                                                                                        |

### 5.3 EXCEPT_003 — CoroutineExceptionHandler and launch vs async

|                  | Description                                                                                                                                                                                                                                                                                             |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Assuming that uncaught exceptions in `async` are handled like in `launch`. In `launch`, they go to the scope's `CoroutineExceptionHandler` (or thread uncaught handler). In `async`, the exception is held in the `Deferred` and only thrown on `await()`. Ignoring the `Deferred` hides the exception. |
| **Recommended**  | Use `CoroutineExceptionHandler` at scope level for uncaught exceptions from `launch`. For `async`, always call `await()` or otherwise handle the `Deferred` so exceptions are not lost.                                                                                                                 |

---

## 6. Testing Coroutines

### 6.1 TEST_001 — Slow Tests with Real Delays

|                  | Description                                                                                                                                                                               |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Tests using `runBlocking` + real `delay()` to simulate network or backoff. This makes tests slow, fragile, and machine-dependent.                                                         |
| **Recommended**  | Use `kotlinx-coroutines-test`: `runTest { }` with virtual time, `advanceTimeBy`, `advanceUntilIdle`, `runCurrent`. Inject dispatchers and replace with `StandardTestDispatcher` in tests. |

### 6.2 TEST_002 — Uncontrolled Fire-and-Forget Coroutines in Tests

|                  | Description                                                                                                                                                                                                  |
|------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Classes launching work in external scopes without a way to control them in tests (can't wait for completion or manipulate time).                                                                             |
| **Recommended**  | Inject `CoroutineScope` (or `TestScope`) and pass a test scope sharing the `TestCoroutineScheduler` with `runTest`. Use `backgroundScope` from `runTest` for parallel processes under the same virtual time. |

### 6.3 TEST_003 — Replacing Dispatchers.Main in Tests

|                  | Description                                                                                                                                                                                     |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Running tests that use `Dispatchers.Main` without replacing it. Behavior depends on the real main thread and can be flaky in CI.                                                                |
| **Recommended**  | Use `Dispatchers.setMain(StandardTestDispatcher())` (or similar) before tests and `Dispatchers.resetMain()` in tearDown so code that uses Main runs deterministically under the test scheduler. |

---

## 7. Channels and Actors

### 7.1 CHANNEL_001 — Forgetting to Close Manual Channels

|                  | Description                                                                                                                                                                   |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Creating `Channel` manually and never calling `close()`. Consumers using `for (x in channel)` will block forever.                                                             |
| **Recommended**  | Use `produce { }` builder which closes the channel automatically when the coroutine terminates. If managing channels manually, clearly define when and where they are closed. |

### 7.2 CHANNEL_002 — Sharing consumeEach Among Multiple Consumers

|                  | Description                                                                                                                                  |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Using `consumeEach` from multiple coroutines on the same channel. `consumeEach` cancels the channel when finished, breaking other consumers. |
| **Recommended**  | For fan-out (multiple consumers), use `for (value in channel)` in each consumer. Reserve `consumeEach` for single-consumer scenarios only.   |

---

## 8. Architecture Patterns

### 8.1 ARCH_001 — General Recommendations

| Pattern                                | Description                                                                                                                                                                                                                           |
|----------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Favor suspend and Flow APIs**        | Prefer over callbacks, `Future`, Rx. Use `suspendCancellableCoroutine` for well-made, cancellable bridges.                                                                                                                            |
| **Maintain structured concurrency**    | Clear scopes, parent/child hierarchy, `coroutineScope` in domain functions, `SupervisorJob` only where decoupling failures makes sense.                                                                                               |
| **Separate responsibilities by layer** | **Data**: repositories with encapsulated suspend APIs. **Domain**: orchestration using `coroutineScope` + `async` for controlled concurrency. **Presentation**: launch in UI scopes respecting lifecycle with automatic cancellation. |
| **Test coroutines properly**           | Use virtual time and controlled scopes instead of waiting for real time to pass.                                                                                                                                                      |

### 8.2 ARCH_002 — Lifecycle-Aware Flow Collection (Android)

|                  | Description                                                                                                                                                                                                               |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Collecting a Flow in Activity/Fragment with `lifecycleScope.launch { flow.collect { } }` without tying collection to lifecycle state. The flow keeps running when the UI goes to background.                              |
| **Recommended**  | Use `repeatOnLifecycle(Lifecycle.State.STARTED)` (or `flowWithLifecycle`) so collection starts when the UI is started and cancels when it stops. This avoids unnecessary work and updates when the screen is not visible. |

---

## 9. Flow

### 9.1 FLOW_001 — Blocking Code in flow { } Builder

|                  | Description                                                                                                                                                                                                                                             |
|------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Performing blocking calls (e.g. `Thread.sleep`, synchronous I/O) inside `flow { }`. The block runs in the collector's context; blocking can freeze the wrong thread (e.g. Main) and flows without suspension points cooperate poorly with cancellation. |
| **Recommended**  | Keep the flow builder non-blocking. Use `flowOn(Dispatchers.IO)` (or another dispatcher) to move emission to a different context, or use suspend APIs inside the builder.                                                                               |

### 9.2 FLOW_002 — Cold vs Hot Flows (StateFlow / SharedFlow)

|                  | Description                                                                                                                                                                                                                                                                                                             |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Using a cold `Flow` for shared state without understanding that each collector triggers a new execution. Or using hot flows (StateFlow/SharedFlow) for one-shot events without configuring replay/buffer.                                                                                                               |
| **Recommended**  | Use **StateFlow** for state (UI state, ViewModel state); it always has a current value and replays it to new collectors. Use **SharedFlow** for events (one-shot, multiple subscribers) with appropriate `replay`, `extraBufferCapacity`, and `onBufferOverflow`. Don't use cold Flow for shared state without caching. |

### 9.3 FLOW_003 — collectLatest Cancels Previous Work

|                  | Description                                                                                                                                                                                          |
|------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Using `collectLatest { }` when the work inside must run to completion. Each new value cancels the previous block; only one "instance" runs at a time.                                                |
| **Recommended**  | Use `collectLatest` only when cancelling in-flight work is acceptable (e.g. search that is replaced by the next query). For work that must complete, use `collect` or handle concurrency explicitly. |

### 9.4 FLOW_004 — SharedFlow Configuration

|                  | Description                                                                                                                                                                                                                                                     |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Bad Practice** | Creating `SharedFlow` with default configuration without considering slow subscribers, replay needs, or backpressure. This can lead to dropped events or blocking.                                                                                              |
| **Recommended**  | Configure `replay`, `extraBufferCapacity`, and `onBufferOverflow` (e.g. `DROP_OLDEST`, `DROP_LATEST`, `SUSPEND`) according to whether you need recent values for new subscribers and how to handle overflow. StateFlow is effectively SharedFlow with replay=1. |

---

## Quick Reference Checklist

Use this checklist when reviewing coroutine code:

- [ ] No `GlobalScope` usage (use framework or injected scopes)
- [ ] `async` calls have corresponding `await` calls
- [ ] Structured concurrency is maintained (children cancelled with parents)
- [ ] `awaitAll` in coroutineScope: first failure cancels others; use supervisorScope if independent
  failures needed
- [ ] No `runBlocking` inside suspend functions
- [ ] Blocking I/O uses `Dispatchers.IO`; suspend functions are main-safe
- [ ] Dispatchers injected for testability where applicable
- [ ] No `Dispatchers.Unconfined` in production
- [ ] No `Job()`/`SupervisorJob()` passed directly to builders
- [ ] Long loops and repeating work have cancellation checks (`yield()`, `ensureActive()`,
  `while (isActive)` + `delay`)
- [ ] `CancellationException` is not swallowed in catch blocks
- [ ] Suspend cleanup uses `withContext(NonCancellable)`
- [ ] Cancelled scopes are not reused
- [ ] `withTimeout`: use `withTimeoutOrNull` or catch `TimeoutCancellationException` to avoid
  cancelling parent scope; ensure resource cleanup on timeout
- [ ] CoroutineExceptionHandler / async: exceptions in async only surface on await()
- [ ] Tests use `runTest` with virtual time; use `setMain`/`resetMain` when testing Main dispatcher
- [ ] Channels are properly closed; `consumeEach` only for single consumer
- [ ] Flow: no blocking in `flow { }`; use StateFlow/SharedFlow appropriately; `collectLatest` only
  when cancellation of previous work is OK
- [ ] (Android) Flow collection with `repeatOnLifecycle(STARTED)` or `flowWithLifecycle`

---

## Tool Implementation Matrix

For each practice, the table below indicates **which tools can implement** it and **caveats** (
severity, false positives, platform scope, or analysis limits).

| §   | Practice                                    | Compiler          | Detekt                                        | Android Lint            | IntelliJ Plugin        | Notes                                                                                                                                       |
|-----|---------------------------------------------|-------------------|-----------------------------------------------|-------------------------|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| 1.1 | GlobalScope usage                           | ✅ Error           | ✅                                             | ✅                       | ✅ Error                | No caveats; well covered.                                                                                                                   |
| 1.2 | async without await                         | ✅ Error           | ✅ (parity)                                    | ✅                       | ✅ Warning              | Compiler: UNUSED_DEFERRED. Exclude when Deferred is passed to another method (heuristic).                                                   |
| 1.3 | Breaking structured concurrency             | ⚠️ Partial        | ✅ ExternalScopeLaunch                         | ✅                       | ✅ UnstructuredLaunch   | Compiler: requires @StructuredScope; Detekt/Lint/IDE can detect launch on external scope from suspend.                                      |
| 1.4 | awaitAll exception propagation              | ❌                 | ⚠️ Heuristic                                  | ⚠️ Heuristic            | ⚠️ Doc/Inspection      | Hard: detect coroutineScope + awaitAll without supervisorScope. Better as documentation or informational inspection.                        |
| 2.1 | Redundant launch in coroutineScope          | ✅ Warning         | ✅                                             | ✅                       | ✅                      | Compiler: REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE. Possible FPs if more than one launch.                                                        |
| 2.2 | runBlocking in suspend                      | ✅ Error           | ✅                                             | ✅                       | ✅ Error                | No caveats; well covered. Exclude main() / JUnit @Test if desired.                                                                          |
| 3.1 | Blocking on wrong dispatchers               | ❌                 | ✅ BlockingCallInCoroutine                     | ✅ MainDispatcherMisuse  | ✅ MainDispatcherMisuse | Detekt/Lint: lists of blocking calls (Thread.sleep, JDBC, etc.). Compiler lacks easy dispatcher context in FIR.                             |
| 3.2 | Main-safe suspend                           | ❌                 | ⚠️ Heuristic                                  | ⚠️ Heuristic            | ⚠️ Heuristic           | Requires “called from Main” analysis; complex. Priority: doc + possible heuristic rule in Detekt/Lint.                                      |
| 3.3 | Dispatchers.Unconfined                      | ✅ Warning         | ✅                                             | ✅                       | ✅ Warning              | No caveats.                                                                                                                                 |
| 3.4 | Job() in builders                           | ✅ Error           | ✅                                             | ✅                       | ✅ Error                | No caveats.                                                                                                                                 |
| 3.5 | Inject Dispatchers                          | ❌                 | ⚠️ Optional                                   | ⚠️ Optional             | ⚠️ Optional            | Style/architecture: detect Dispatchers.X in constructors/functions. Many FPs (tests, main). Better as doc/skill recommendation.             |
| 4.1 | Loops without cooperation                   | ⚠️ Warning viable | ✅ LoopWithoutYield                            | ✅ (partial)             | ✅ + Quick fix          | Compiler: for/while loops in suspend without delay/yield/ensureActive; possible FPs in very small loops. IDE quick fix: add ensureActive(). |
| 4.2 | Periodic work (repeating)                   | ⚠️ Same as 4.1    | ✅ (same rule)                                 | ✅ (same rule)           | ✅                      | Same implementation as 4.1; while(true) without isActive/delay is a subset.                                                                 |
| 4.3 | Swallowing CancellationException            | ✅ Warning         | ✅ (parity)                                    | ✅                       | ✅ Warning              | Compiler: also in suspend lambdas (scope.launch { try/catch }).                                                                             |
| 4.4 | Suspend in finally without NonCancellable   | ✅ Warning         | ✅ (parity)                                    | ✅                       | ✅ + Quick fix          | No caveats.                                                                                                                                 |
| 4.5 | Reusing cancelled scope                     | ⚠️ Optional       | ✅ ScopeReuseAfterCancel                       | ✅ ScopeReuseAfterCancel | ✅ + Quick fix          | Detekt/Lint/IDE: scope.cancel() followed by scope.launch/async in same function; messages include CANCEL_005 and doc link; IDE quick fix replaces cancel() with cancelChildren(). Compiler: costlier (control flow). |
| 4.6 | withTimeout scope cancellation              | ❌                 | ⚠️ Heuristic                                  | ⚠️ Heuristic            | ⚠️ Heuristic           | Detect withTimeout without try/catch TimeoutCancellationException or withTimeoutOrNull. Possible FPs if caught in upper layer.              |
| 4.7 | withTimeout resource cleanup                | ❌                 | ❌                                             | ❌                       | ❌                      | Not reliably automatable; doc/skill.                                                                                                        |
| 5.1 | SupervisorJob in single builder             | ✅ Error           | ✅                                             | ✅                       | ✅ Error                | No caveats.                                                                                                                                 |
| 5.2 | CancellationException for domain            | ✅ Error           | ✅                                             | ✅                       | ✅                      | No caveats.                                                                                                                                 |
| 5.3 | CoroutineExceptionHandler / launch vs async | ❌                 | ❌                                             | ❌                       | ⚠️ Doc/Inspection      | Explain in async without await inspection description. No new rule; strengthen message.                                                     |
| 6.1 | runBlocking + delay in tests                | ❌                 | ✅ RunBlockingWithDelayInTest                  | ✅                       | ⚠️ Intention           | Detekt/Lint: test files (name/package). Compiler: possible but requires test context. IDE intention: “Convert to runTest”.                  |
| 6.2 | Uncontrolled fire-and-forget in tests       | ❌                 | ✅ ExternalScopeLaunch (in test)               | ✅                       | ⚠️                     | In test modules, detect use of external scope without injection. Heuristic by file/package name.                                            |
| 6.3 | setMain / resetMain in tests                | ❌                 | ⚠️ Optional                                   | ⚠️ Optional             | ⚠️ Optional            | Detect tests using Dispatchers.Main without setMain. May FP if Main is injected. Better as doc.                                             |
| 7.1 | Channel not closed                          | ❌                 | ✅ ChannelNotClosed                           | ✅ ChannelNotClosed      | ⚠️ Heuristic           | Heuristic: Channel() without close() on any path; produce closes automatically. Compiler: complex flow analysis.                            |
| 7.2 | consumeEach multiple consumers              | ❌                 | ✅ ConsumeEachMultipleConsumers               | ✅ ConsumeEachMultipleConsumers | ⚠️ Heuristic           | Same Channel in multiple launch/async with consumeEach. Heuristic.                                                                          |
| 8.1 | General architecture                        | —                 | —                                             | —                       | —                      | Documentation and skill only.                                                                                                               |
| 8.2 | Lifecycle-aware Flow (Android)              | ❌                 | ❌                                             | ✅ (to implement)        | ⚠️ Android             | Lint: detect collect in lifecycleScope without repeatOnLifecycle/flowWithLifecycle. Android-specific.                                       |
| 9.1 | Blocking in flow { }                        | ❌                 | ✅ FlowBlockingCall                            | ✅ FlowBlockingCall       | ⚠️ Heuristic           | Detekt/Lint: blocking calls (Thread.sleep, I/O, JDBC, etc.) inside flow { }; use flowOn(Dispatchers.IO) or suspend APIs.                      |
| 9.2 | Cold vs hot (StateFlow/SharedFlow)          | ❌                 | ❌                                             | ❌                       | ⚠️ Doc                 | Decision guide; hard to automate “should be StateFlow”.                                                                                     |
| 9.3 | collectLatest semantics                     | ❌                 | ❌                                             | ❌                       | ⚠️ Doc                 | Documentation; no automatic rule.                                                                                                           |
| 9.4 | SharedFlow configuration                    | ❌                 | ⚠️ Optional                                   | ❌                       | ❌                      | SharedFlow() without params or with defaults; optional suggestion in Detekt.                                                                |

### Legend

- **✅** = Implemented or planned in that tool.
- **⚠️** = Implementable with heuristics, limited context, or only in certain cases (
  partial/optional).
- **❌** = Not recommended or not viable for that tool.
- **Notes** = False positives, default severity, scope (KMP/Android), or analysis limits.

### Summary by tool

| Tool             | Rules well covered                                                 | Heuristic/partial rules                     | Candidates to add                                            |
|------------------|--------------------------------------------------------------------|---------------------------------------------|--------------------------------------------------------------|
| **Compiler**     | 1.1, 1.2, 2.1, 2.2, 3.3, 3.4, 4.3, 4.4, 5.1, 5.2                   | 1.3, 4.1, 4.5                               | 4.1 (LoopWithoutYield) as warning                            |
| **Detekt**       | 1.1–1.3, 2.1, 2.2, 3.1, 3.3, 3.4, 4.1, 4.3–4.5, 5.1, 5.2, 6.1, 6.2, 7.1, 7.2 | 1.4, 4.6, 6.3, 3.5                          | 9.1; optional 4.6, 9.4                                       |
| **Android Lint** | Same as Detekt + 3.1 Main, 8.2 lifecycle Flow, 7.1, 7.2            | 1.4, 4.5, 4.6, 6.2, 6.3                     | 8.2, 9.1                                                     |
| **IntelliJ**     | Parity with Compiler/Lint + quick fixes/intentions                 | 1.4, 4.6, 5.3, 6.1, 6.3, 7.1, 7.2, 8.2, 9.1 | Intentions runTest, ensureActive; messages citing §5.3, §4.6 |

---

## License

```
Copyright 2026 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```
