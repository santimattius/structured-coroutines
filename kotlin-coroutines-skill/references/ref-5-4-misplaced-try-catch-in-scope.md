
# 5.4 EXCEPT_004 — Misplaced try-catch inside coroutineScope

## Bad Practice

Placing a `try/catch` inside the `coroutineScope { }` lambda around `await()` or `join()` of an
`async`/`launch` created in that same lambda. The child is a structural child of the scope: its
failure cancels the scope and `coroutineScope` rethrows to its caller, so the catch cannot contain
the failure (it may even run, which is misleading).

```kotlin
// BAD: the catch cannot stop the failure from propagating out of coroutineScope
suspend fun load(): Int = coroutineScope {
    val d = async<Int> { error("boom") }
    try {
        d.await()
    } catch (e: Exception) {
        0
    }
}
```

## Recommended

Wrap the whole `coroutineScope` call in `try/catch`, catch inside the child block, or use
`supervisorScope` (only its direct children are isolated).

```kotlin
// GOOD: handle the failure around the whole scope
suspend fun load(): Int =
    try {
        coroutineScope { async<Int> { error("boom") }.await() }
    } catch (e: Exception) {
        0
    }

// GOOD: handle the failure inside the child
suspend fun load(): Int = coroutineScope {
    async { try { error("boom") } catch (e: Exception) { 0 } }.await()
}
```

## Why

`coroutineScope` is a structured parent: a failing child cancels the scope and the exception is
rethrown by `coroutineScope` itself, regardless of any `try/catch` around `await()` inside the
lambda. Containment must happen outside the scope or inside the child.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `coroutineScope { try { d.await() } catch (...) { } }` | `try { coroutineScope { ... d.await() } } catch (...) { }`, or catch inside the `async`/`launch` block |

## Toolkit

Rule `EXCEPT_004` — Compiler `MISPLACED_TRY_CATCH_IN_SCOPE`; Detekt, Lint and IntelliJ
`MisplacedTryCatchInScope`. Default severity: warning.
