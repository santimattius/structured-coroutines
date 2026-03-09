# 3.2 DISPATCH_002 — Main-Safe Suspend Functions

## Bad Practice

Exposing suspend functions that perform blocking work on the calling thread. Callers on the main
thread cause ANRs or freezes, even though the function appears non-blocking because it is `suspend`.

```kotlin
// BAD: blocks the calling thread (e.g. Main) despite being suspend
suspend fun loadUserProfile(id: String): User {
    return database.queryUser(id) // blocking JDBC call; no dispatcher switch
}
```

## Recommended

Suspend functions should be **main-safe**: safe to call from any thread, including Main. Move
blocking work inside `withContext(Dispatchers.IO)` (or the appropriate dispatcher) so the function
never blocks the caller's thread.

```kotlin
// GOOD: always safe to call from Main or any thread
suspend fun loadUserProfile(id: String): User = withContext(Dispatchers.IO) {
    database.queryUser(id) // blocking call executed on IO thread pool
}
```

## Why

A `suspend` keyword alone does not guarantee non-blocking behaviour. If the function body does
blocking I/O without switching dispatcher, it runs on whatever thread called it. If that thread is
Main, the UI freezes. Making suspend functions main-safe is a contract: callers should not have to
know the implementation details to use the function safely.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `suspend fun load(): T { return blockingOp() }` | `suspend fun load(): T = withContext(Dispatchers.IO) { blockingOp() }` |
