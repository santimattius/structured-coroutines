# 4.1 Ignoring Cancellation in Intensive Loops

## Bad Practice

Long loops or heavy computation **without suspension points or cancellation checks**. The coroutine won’t respond to cancellation until the calculation finishes or a delay occurs.

## Recommended

- Add **cooperation points**: **`yield()`** periodically in CPU-intensive suspend functions, or **`coroutineContext.isActive`** / **`ensureActive()`** in loops.
- For blocking code, use an appropriate dispatcher and cancellable APIs where available.

## Why

Coroutine cancellation is cooperative. If the coroutine never suspends and doesn’t check `isActive`, it will keep running until the end. That can block shutdown or waste resources when the user or parent has already requested cancellation.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `while (true) { heavyComputation() }` in a coroutine | `while (isActive) { heavyComputation(); yield() }` or `ensureActive()` in the loop |
| Large list processing without checks | Add `yield()` or `ensureActive()` every N iterations. |
