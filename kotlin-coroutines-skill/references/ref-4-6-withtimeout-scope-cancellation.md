# 4.6 CANCEL_006 — withTimeout and Scope Cancellation

## Bad Practice

Using `withTimeout` without handling `TimeoutCancellationException`. Uncaught, it cancels the
**parent scope** (not just the timeout block), so sibling coroutines can be cancelled unexpectedly.

```kotlin
// BAD: timeout propagates and cancels the parent scope
viewModelScope.launch {
    val data = withTimeout(3_000) { fetchData() } // TimeoutCancellationException → parent cancelled
    updateUI(data) // never reached; sibling launches also cancelled
}
```

## Recommended

Prefer `withTimeoutOrNull` when you want "timeout → null" without affecting the scope. If using
`withTimeout`, catch `TimeoutCancellationException` explicitly so it does not propagate and cancel
the scope.

```kotlin
// GOOD option A: withTimeoutOrNull returns null on timeout; scope unaffected
val data = withTimeoutOrNull(3_000) { fetchData() }
if (data == null) showTimeoutError()

// GOOD option B: catch explicitly to handle timeout and keep scope alive
val data = try {
    withTimeout(3_000) { fetchData() }
} catch (e: TimeoutCancellationException) {
    showTimeoutError()
    return@launch
}
```

## Why

`TimeoutCancellationException` is a subclass of `CancellationException`. When uncaught, it
propagates up the coroutine tree and cancels the parent scope, not just the timed-out block. This
is a common source of subtle bugs where multiple coroutines stop unexpectedly because one timed out.
`withTimeoutOrNull` is the safest default when you only need a fallback value.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `withTimeout(n) { op() }` (unhandled) | `withTimeoutOrNull(n) { op() } ?: fallback` |
