# 4.3 Suspendable Cleanup Without NonCancellable

## Bad Practice

Making **suspend calls in `finally` blocks** (e.g. DB writes, closing remote sessions) **without `withContext(NonCancellable)`**. If the coroutine is cancelling, any suspension can throw `CancellationException` again and cleanup may not run.

## Recommended

For critical cleanup that **must suspend**, wrap the suspend calls in **`withContext(NonCancellable) { }`** inside `finally`.

## Why

When a coroutine is cancelled, further suspend points throw `CancellationException`. A `finally` block that calls `delay()` or other suspend functions can throw before the rest of the cleanup runs. `NonCancellable` allows that block to run to completion despite cancellation.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `finally { db.close() }` where `close()` is suspend | `finally { withContext(NonCancellable) { db.close() } }` |
| `finally { sendLogToServer() }` (suspend) | `finally { withContext(NonCancellable) { sendLogToServer() } }` |
