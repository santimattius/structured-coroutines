# 5.2 Extending CancellationException for Domain Errors

## Bad Practice

Defining **domain errors** that inherit from **`CancellationException`** (e.g. `class UserNotFoundException : CancellationException()`). These exceptions **don’t propagate upward** like normal exceptions; they only cancel the current coroutine and children, so callers can’t handle them as business errors.

## Recommended

- Use normal **`Exception`** or **`RuntimeException`** (or your own domain hierarchy) for **domain/business errors**.
- Reserve **`CancellationException`** for **true cancellation** (user cancel, timeout, scope cancelled) only.

## Why

`CancellationException` is treated specially by the coroutines runtime: it’s used to propagate cancellation and is often not logged or propagated as a “failure”. If you use it for “user not found” or similar, callers won’t see it as a regular error and handling (e.g. UI messages, retries) becomes inconsistent.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `class UserNotFoundException : CancellationException()` | `class UserNotFoundException : RuntimeException()` or `Exception()` |
| `throw CancellationException()` for “no data” | Use a dedicated domain exception (e.g. `NoDataException`) that does not extend `CancellationException`. |
