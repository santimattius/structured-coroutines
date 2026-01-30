# Kotlin Coroutines Best Practices

A comprehensive guide to good and bad practices when working with Kotlin Coroutines and structured concurrency.

## Table of Contents

- [1. Asynchronous Builders and Scopes](#1-asynchronous-builders-and-scopes)
- [2. Blocking Builders and runBlocking](#2-blocking-builders-and-runblocking)
- [3. Context and Dispatchers](#3-context-and-dispatchers)
- [4. Job, Lifecycle, and Cancellation](#4-job-lifecycle-and-cancellation)
- [5. Exceptions and SupervisorJob](#5-exceptions-and-supervisorjob)
- [6. Testing Coroutines](#6-testing-coroutines)
- [7. Channels and Actors](#7-channels-and-actors)
- [8. Architecture Patterns](#8-architecture-patterns)

---

## 1. Asynchronous Builders and Scopes

### 1.1 Using GlobalScope in Production Code

| | Description |
|---|-------------|
| **Bad Practice** | Launching coroutines in `GlobalScope` (e.g., `GlobalScope.launch { }` or `GlobalScope.async { }`). This breaks the coroutine tree, preventing proper cancellation and exception propagation, making it difficult to reason about job lifetimes. |
| **Recommended** | Always use a meaningful `CoroutineScope`: framework scopes (`viewModelScope`, `lifecycleScope`, `rememberCoroutineScope`), custom injected scopes (`applicationScope`, `backgroundScope`), or local scopes in suspend functions (`coroutineScope { }`, `withContext { }`). Only launch independent processes in an external scope when absolutely necessary, and document it clearly. |

### 1.2 Using async Without Calling await

| | Description |
|---|-------------|
| **Bad Practice** | Using `async` just to launch work without consuming the `Deferred` (e.g., `scope.async { doWork() }` without `await()`). This confuses readers and can hide exceptions inside the `Deferred`. |
| **Recommended** | Use `launch` for fire-and-forget work and `async` only when you need a return value. Simple rule: if you never call `await`, you should use `launch`. |

### 1.3 Breaking Structured Concurrency

| | Description |
|---|-------------|
| **Bad Practice** | Inside a suspend function or `coroutineScope`, launching work in an external scope without justification (e.g., `backgroundScope.launch { }`). This means work won't be cancelled with the caller, complicating resource cleanup, testing, and traceability. |
| **Recommended** | Respect structured concurrency by default. Inside suspend functions, create subtasks with `coroutineScope { }` + `async`/`launch`. Only use external scopes for truly background processes that must survive the current flow (e.g., offline analytics, deferred cache writes). |

---

## 2. Blocking Builders and runBlocking

### 2.1 Using launch on the Last Line of coroutineScope

| | Description |
|---|-------------|
| **Bad Practice** | Ending a suspend function with `coroutineScope { launch { } }`. This appears to be fire-and-forget, but `coroutineScope` still waits for all children to complete. |
| **Recommended** | If you want the function to wait, execute the body directly inside `coroutineScope` without wrapping in `launch`. If you truly don't want to wait, launch in an explicit external scope to make it clear you're breaking structured concurrency. |

### 2.2 Using runBlocking Inside Suspend Functions

| | Description |
|---|-------------|
| **Bad Practice** | Calling `runBlocking` from coroutine-based code, especially inside suspend functions. This blocks the current thread, breaks the non-blocking model, and can cause deadlocks or ANRs on Android. |
| **Recommended** | Use `runBlocking` only as a bridge from purely blocking code to coroutines (legacy entry points, console scripts, interop with blocking APIs). Inside suspend functions, use suspend versions of libraries or wrap blocking operations with `withContext(Dispatchers.IO)`. |

---

## 3. Context and Dispatchers

### 3.1 Mixing Blocking Code with Wrong Dispatchers

| | Description |
|---|-------------|
| **Bad Practice** | Performing blocking I/O (files, JDBC, synchronous libraries) on `Dispatchers.Default` or `Dispatchers.Main`. This can freeze the UI or exhaust the thread pool through prolonged blocking. |
| **Recommended** | Use `Dispatchers.Default` for CPU-bound work, `Dispatchers.Main.immediate` for UI, and `withContext(Dispatchers.IO)` or a limited parallelism dispatcher for blocking operations. Prefer suspend APIs over wrapping synchronous APIs. |

### 3.2 Abusing Dispatchers.Unconfined

| | Description |
|---|-------------|
| **Bad Practice** | Using `Dispatchers.Unconfined` in production to avoid thread switches. Code runs on whatever thread resumes it, making execution unpredictable and potentially running blocking calls on the UI thread. |
| **Recommended** | Reserve `Dispatchers.Unconfined` for special cases or legacy testing. In production, always choose an appropriate dispatcher: `Default`, `Main`, `IO`, single-thread, or Loom. |

### 3.3 Passing Job() Directly as Context to Builders

| | Description |
|---|-------------|
| **Bad Practice** | Using `launch(Job()) { }` or `withContext(SupervisorJob()) { }` to control errors. This breaks the parent-child relationship and structured concurrency: the new Job becomes an independent parent. |
| **Recommended** | Let builders use the Job from the current scope to maintain the coroutine tree. For supervisor semantics, use `supervisorScope { }` inside suspend functions or a well-defined `CoroutineScope(SupervisorJob() + dispatcher + handler)`. |

---

## 4. Job, Lifecycle, and Cancellation

### 4.1 Ignoring Cancellation in Intensive Loops

| | Description |
|---|-------------|
| **Bad Practice** | Long loops or heavy computation without suspension points or cancellation checks. The coroutine won't respond to cancellation until calculation finishes or a delay occurs. |
| **Recommended** | Insert cooperation points: `yield()` periodically in CPU-intensive suspend functions, or check `coroutineContext.isActive` / `ensureActive()` in loops. For blocking code, use an appropriate dispatcher with cancellable APIs. |

### 4.2 Swallowing CancellationException

| | Description |
|---|-------------|
| **Bad Practice** | Catching `Exception` and treating `CancellationException` like any other error. This prevents cancellation from propagating correctly and can leave coroutines alive when they should terminate. |
| **Recommended** | Treat `CancellationException` separately: `catch (e: CancellationException) { throw e }` before catching other exceptions. Alternative: call `ensureActive()` inside catch blocks to re-throw if cancelled. |

### 4.3 Suspendable Cleanup Without NonCancellable

| | Description |
|---|-------------|
| **Bad Practice** | Making suspend calls in `finally` blocks (DB writes, closing remote sessions) without `withContext(NonCancellable)`. If the coroutine is cancelling, any suspension throws `CancellationException` again and cleanup may not execute. |
| **Recommended** | For critical cleanup that needs to suspend, use `withContext(NonCancellable) { }` inside `finally`. |

### 4.4 Reusing a Cancelled CoroutineScope

| | Description |
|---|-------------|
| **Bad Practice** | Calling `scope.cancel()` and then trying to launch more coroutines in that scope. A cancelled Job doesn't accept new children, and subsequent launches fail silently. |
| **Recommended** | To clean up children but keep the scope usable, use `coroutineContext.job.cancelChildren()`. Only cancel the Job completely when the scope will no longer be reused. |

---

## 5. Exceptions and SupervisorJob

### 5.1 Using SupervisorJob as an Argument in a Single Builder

| | Description |
|---|-------------|
| **Bad Practice** | Passing `SupervisorJob()` to protect a single coroutine: `launch(SupervisorJob()) { }`. The SupervisorJob becomes the parent, but the real parent still uses a normal Job which gets cancelled on exceptions. |
| **Recommended** | Use SupervisorJob at the scope level: `CoroutineScope(SupervisorJob() + dispatcher + handler)` for independent children, or `supervisorScope { }` when children shouldn't cancel each other inside a suspend function. |

### 5.2 Extending CancellationException for Domain Errors

| | Description |
|---|-------------|
| **Bad Practice** | Defining domain errors that inherit from `CancellationException` (e.g., `class UserNotFoundException : CancellationException()`). These exceptions don't propagate upward normally; they only cancel the current coroutine and children. |
| **Recommended** | Use normal `Exception` or `RuntimeException` for domain errors. Reserve `CancellationException` for true cancellation cases only. |

---

## 6. Testing Coroutines

### 6.1 Slow Tests with Real Delays

| | Description |
|---|-------------|
| **Bad Practice** | Tests using `runBlocking` + real `delay()` to simulate network or backoff. This makes tests slow, fragile, and machine-dependent. |
| **Recommended** | Use `kotlinx-coroutines-test`: `runTest { }` with virtual time, `advanceTimeBy`, `advanceUntilIdle`, `runCurrent`. Inject dispatchers and replace with `StandardTestDispatcher` in tests. |

### 6.2 Uncontrolled Fire-and-Forget Coroutines in Tests

| | Description |
|---|-------------|
| **Bad Practice** | Classes launching work in external scopes without a way to control them in tests (can't wait for completion or manipulate time). |
| **Recommended** | Inject `CoroutineScope` (or `TestScope`) and pass a test scope sharing the `TestCoroutineScheduler` with `runTest`. Use `backgroundScope` from `runTest` for parallel processes under the same virtual time. |

---

## 7. Channels and Actors

### 7.1 Forgetting to Close Manual Channels

| | Description |
|---|-------------|
| **Bad Practice** | Creating `Channel` manually and never calling `close()`. Consumers using `for (x in channel)` will block forever. |
| **Recommended** | Use `produce { }` builder which closes the channel automatically when the coroutine terminates. If managing channels manually, clearly define when and where they are closed. |

### 7.2 Sharing consumeEach Among Multiple Consumers

| | Description |
|---|-------------|
| **Bad Practice** | Using `consumeEach` from multiple coroutines on the same channel. `consumeEach` cancels the channel when finished, breaking other consumers. |
| **Recommended** | For fan-out (multiple consumers), use `for (value in channel)` in each consumer. Reserve `consumeEach` for single-consumer scenarios only. |

---

## 8. Architecture Patterns

### General Recommendations

| Pattern | Description |
|---------|-------------|
| **Favor suspend and Flow APIs** | Prefer over callbacks, `Future`, Rx. Use `suspendCancellableCoroutine` for well-made, cancellable bridges. |
| **Maintain structured concurrency** | Clear scopes, parent/child hierarchy, `coroutineScope` in domain functions, `SupervisorJob` only where decoupling failures makes sense. |
| **Separate responsibilities by layer** | **Data**: repositories with encapsulated suspend APIs. **Domain**: orchestration using `coroutineScope` + `async` for controlled concurrency. **Presentation**: launch in UI scopes respecting lifecycle with automatic cancellation. |
| **Test coroutines properly** | Use virtual time and controlled scopes instead of waiting for real time to pass. |

---

## Quick Reference Checklist

Use this checklist when reviewing coroutine code:

- [ ] No `GlobalScope` usage (use framework or injected scopes)
- [ ] `async` calls have corresponding `await` calls
- [ ] Structured concurrency is maintained (children cancelled with parents)
- [ ] No `runBlocking` inside suspend functions
- [ ] Blocking I/O uses `Dispatchers.IO`
- [ ] No `Dispatchers.Unconfined` in production
- [ ] No `Job()`/`SupervisorJob()` passed directly to builders
- [ ] Long loops have cancellation checks (`yield()`, `ensureActive()`)
- [ ] `CancellationException` is not swallowed in catch blocks
- [ ] Suspend cleanup uses `withContext(NonCancellable)`
- [ ] Cancelled scopes are not reused
- [ ] Tests use `runTest` with virtual time
- [ ] Channels are properly closed

---

## License

```
Copyright 2026 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```
