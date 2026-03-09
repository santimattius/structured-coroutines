# 4.7 CANCEL_007 — withTimeout and Resource Cleanup

## Bad Practice

Opening resources (files, connections, locks) inside a `withTimeout` block without ensuring cleanup
when the timeout fires. The timeout can interrupt at any moment, leaving resources in a leaked state.

```kotlin
// BAD: connection may leak if timeout fires after open() but before close()
suspend fun fetchWithTimeout(): String = withTimeout(2_000) {
    val connection = openConnection()  // opens resource
    val result = connection.read()     // timeout may interrupt here
    connection.close()                 // may never execute
    result
}
```

## Recommended

Ensure cleanup in `finally` or with `withContext(NonCancellable) { }` when the block is interrupted
by timeout. Design so that resource lifecycle is well-defined on both success and timeout paths.

```kotlin
// GOOD: resource is always cleaned up, even on timeout
suspend fun fetchWithTimeout(): String? = withTimeoutOrNull(2_000) {
    val connection = openConnection()
    try {
        connection.read()
    } finally {
        // withContext(NonCancellable) needed if close() is suspend
        withContext(NonCancellable) { connection.close() }
    }
}
```

## Why

`withTimeout` throws `TimeoutCancellationException` asynchronously at the next suspension point.
Any code after the interrupted suspension does not execute unless it is in a `finally` block.
Without `withContext(NonCancellable)` inside `finally`, suspend calls inside the cleanup themselves
throw `CancellationException` again, preventing cleanup from completing.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| Resource opened in `withTimeout` without `finally` | Wrap resource in `try/finally`; use `withContext(NonCancellable)` for suspend cleanup calls |
