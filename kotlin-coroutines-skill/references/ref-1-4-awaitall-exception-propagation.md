# 1.4 SCOPE_004 — awaitAll and Exception Propagation

## Bad Practice

Assuming that `coroutineScope { awaitAll(deferred1, deferred2, ...) }` runs tasks independently.
The first exception cancels **all** other deferreds (structured concurrency: siblings are cancelled
when one fails).

```kotlin
// BAD: first failure cancels all other downloads
suspend fun downloadAll(urls: List<String>): List<String> = coroutineScope {
    val deferreds = urls.map { url -> async { download(url) } }
    awaitAll(*deferreds.toTypedArray()) // first failure cancels the rest
}
```

## Recommended

Use `supervisorScope { awaitAll(...) }` and handle each `Deferred`'s exception separately when
you need independent failure semantics. In `coroutineScope`, the first failure cancels the rest.

```kotlin
// GOOD: each download is independent; collect results and errors separately
suspend fun downloadAll(urls: List<String>): List<Result<String>> = supervisorScope {
    val deferreds = urls.map { url -> async { download(url) } }
    deferreds.map { deferred ->
        runCatching { deferred.await() }
    }
}
```

## Why

`coroutineScope` propagates the first exception upward and cancels all siblings. This is correct
behaviour when tasks are interdependent (all-or-nothing). When failures should be independent,
use `supervisorScope` so a single child's failure does not cancel the others. Without this
distinction, one failing URL silently aborts every other in-progress download.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `coroutineScope { awaitAll(...) }` (independent tasks) | `supervisorScope { deferreds.map { runCatching { it.await() } } }` |
