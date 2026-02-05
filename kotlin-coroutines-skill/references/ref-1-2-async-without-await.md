# 1.2 Using async Without Calling await

## Bad Practice

Using `async` just to launch work without consuming the `Deferred` (e.g., `scope.async { doWork() }` without `await()`). This confuses readers and can hide exceptions inside the `Deferred`.

## Recommended

- Use **`launch`** for fire-and-forget work.
- Use **`async`** only when you need a return value.
- Simple rule: **if you never call `await`, you should use `launch`.**

## Why

Unconsumed `Deferred` hides failures (exceptions are only thrown when `await()` is called). Using `async` without `await` also suggests parallelism that isn’t being used and makes the code’s intent unclear.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `scope.async { doWork() }` (no await) | `scope.launch { doWork() }` |
| When you need a result: `scope.async { doWork() }` and never await | Either `launch { doWork() }` or `async { doWork() }.await()` (or use inside `coroutineScope { async { }.await() }`) |
