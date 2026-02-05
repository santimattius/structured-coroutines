# 4.2 Swallowing CancellationException

## Bad Practice

Catching **`Exception`** and treating **`CancellationException`** like any other error. This prevents cancellation from propagating and can leave coroutines alive when they should terminate.

## Recommended

- Treat **`CancellationException`** separately: **`catch (e: CancellationException) { throw e }`** (rethrow) **before** catching other exceptions.
- Alternative: call **`ensureActive()`** inside catch blocks so that if the coroutine is cancelled, it rethrows.

## Why

`CancellationException` is how cancellation is propagated. If you catch it and don’t rethrow, the coroutine won’t cancel and the parent may assume it has stopped. Always rethrow so cancellation continues to propagate.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `catch (e: Exception) { log(e); Result.failure(e) }` | `catch (e: CancellationException) { throw e }; catch (e: Exception) { log(e); Result.failure(e) }` |
| `catch (e: Throwable) { ... }` without rethrowing cancellation | Add a dedicated `catch (e: CancellationException) { throw e }` first. |
