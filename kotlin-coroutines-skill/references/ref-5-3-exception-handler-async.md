# 5.3 EXCEPT_003 — CoroutineExceptionHandler and launch vs async

## Bad Practice

Assuming that uncaught exceptions in `async` are handled like in `launch`. In `launch`, they go to
the scope's `CoroutineExceptionHandler`. In `async`, the exception is held in the `Deferred` and
only thrown when `await()` is called. Ignoring the `Deferred` silently hides the exception.

```kotlin
// BAD: exception in async is never surfaced; CEH does NOT catch it
val scope = CoroutineScope(SupervisorJob() + exceptionHandler)

scope.launch {
    val deferred = async { riskyOperation() } // exception stored in Deferred
    // forgot to call deferred.await() → exception is permanently lost
}
```

## Recommended

Use `CoroutineExceptionHandler` at scope level for uncaught exceptions from `launch`. For `async`,
always call `await()` (or handle the `Deferred`) so exceptions are not lost.

```kotlin
// GOOD: launch → CEH catches it
val handler = CoroutineExceptionHandler { _, e -> logError(e) }
val scope = CoroutineScope(SupervisorJob() + handler)

scope.launch {
    riskyOperation() // uncaught exception → goes to handler
}

// GOOD: async → must await to surface exception
scope.launch {
    val deferred = async { riskyOperation() }
    try {
        val result = deferred.await() // exception thrown here
        use(result)
    } catch (e: Exception) {
        handleError(e)
    }
}
```

## Why

`async` uses a two-stage exception model: the exception is stored in the `Deferred` on failure and
only thrown when `await()` is called. `CoroutineExceptionHandler` only intercepts exceptions that
propagate through the coroutine tree (i.e. from `launch`). Uncollected `Deferred` results are
silently discarded, hiding errors that may leave the application in an inconsistent state.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `async { ... }` result ignored (no `await`) | Always call `deferred.await()` or use `awaitAll()`; wrap in `try/catch` or `runCatching` |
