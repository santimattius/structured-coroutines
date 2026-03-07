# 4.2 CANCEL_002 — Periodic or Repeating Work

## Bad Practice

Implementing polling or periodic tasks with infinite loops that lack suspension points or `isActive`
checks. The coroutine becomes a "zombie" that keeps running after the scope is cancelled.

```kotlin
// BAD: infinite loop without cancellation cooperation; won't stop when scope cancels
fun startPolling(scope: CoroutineScope) = scope.launch {
    while (true) {
        fetchLatestData()
        Thread.sleep(5_000) // blocks thread; ignores cancellation
    }
}
```

## Recommended

Use a cancellation-cooperative pattern: `while (isActive)` with `ensureActive()` or `yield()` and
`delay(interval)` inside the loop. This allows the scope to cancel the repeating task cleanly.

```kotlin
// GOOD: cooperative periodic work; stops when scope is cancelled
fun startPolling(scope: CoroutineScope) = scope.launch {
    while (isActive) {
        ensureActive()         // check before each iteration
        fetchLatestData()
        delay(5_000)           // suspend point; responds to cancellation
    }
}
```

## Why

Without suspension points (`delay`, `yield`, `ensureActive`), a coroutine in an infinite loop does
not respond to cancellation. The job appears cancelled in the scope but the loop keeps running on
its thread, wasting resources and potentially causing unexpected side effects after the lifecycle
ends. `delay` is both a suspension point and a cancellation check.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `while (true) { work(); Thread.sleep(n) }` | `while (isActive) { ensureActive(); work(); delay(n) }` |
