# 3.5 DISPATCH_005 — Injecting Dispatchers for Testability

## Bad Practice

Hardcoding `Dispatchers.Main`, `Dispatchers.IO`, or `Dispatchers.Default` inside classes or
suspend functions, making tests dependent on real threads and platform-specific thread schedulers.

```kotlin
// BAD: hardcoded dispatcher; tests run on real thread pool, may be flaky
class UserRepository(private val db: Database) {
    suspend fun getUser(id: String): User = withContext(Dispatchers.IO) {
        db.findUser(id)
    }
}
```

## Recommended

Inject `CoroutineDispatcher` with sensible production defaults. In tests, replace with
`StandardTestDispatcher` or `UnconfinedTestDispatcher` for deterministic, virtual-time execution.

```kotlin
// GOOD: dispatcher injected; testable with TestDispatcher
class UserRepository(
    private val db: Database,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getUser(id: String): User = withContext(ioDispatcher) {
        db.findUser(id)
    }
}

// In tests:
val testDispatcher = StandardTestDispatcher()
val repo = UserRepository(fakeDb, ioDispatcher = testDispatcher)
runTest(testDispatcher) {
    val user = repo.getUser("1")
    // deterministic, virtual time
}
```

## Why

Tests that rely on real thread pools (`Dispatchers.IO`, `Dispatchers.Default`) are slow, flaky in
CI, and cannot leverage virtual time. Injecting dispatchers decouples production behaviour from
test control, enabling fast, deterministic tests with `TestScope` and `advanceUntilIdle`.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `withContext(Dispatchers.IO) { ... }` (hardcoded) | `withContext(ioDispatcher) { ... }` where `ioDispatcher` is a constructor parameter defaulting to `Dispatchers.IO` |
