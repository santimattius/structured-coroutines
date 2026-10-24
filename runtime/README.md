# Structured Coroutines Runtime

Optional multiplatform helpers that are the **compliant replacement** for patterns the compiler
plugin, Detekt rules and Android Lint rules flag. Instead of only telling you what not to write,
the toolkit ships the safe version.

> **Experimental.** `:runtime` is on its own `0.x` line and every declaration is gated by
> `@ExperimentalStructuredCoroutines`. The API may change or be removed without notice until the
> gate is lifted. See [Stability](#stability-and-caveats).

## Installation

Artifacts (group `io.github.santimattius`):

| Artifact                                | Purpose                                   |
|-----------------------------------------|-------------------------------------------|
| `structured-coroutines-runtime`         | The helpers described below               |
| `structured-coroutines-runtime-test`    | Test doubles (`TestDispatcherProvider`)   |

Both versions come from the `RUNTIME_VERSION` property (currently `0.1.0`), independent of the
toolkit's `PROJECT_VERSION`.

```kotlin
// build.gradle.kts (Kotlin Multiplatform)
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.santimattius:structured-coroutines-runtime:0.1.0")
        }
        commonTest.dependencies {
            implementation("io.github.santimattius:structured-coroutines-runtime-test:0.1.0")
        }
    }
}
```

For a JVM-only project use `implementation(...)` and `testImplementation(...)` instead.
`kotlinx-coroutines-core` is exposed as an `api` dependency of `:runtime`, and
`kotlinx-coroutines-test` as an `api` dependency of `:runtime-test` (so production code never gets
the test library transitively).

### Opting in

Every API requires opting in to `@ExperimentalStructuredCoroutines` (a warning-level opt-in).
Either annotate the usage:

```kotlin
@OptIn(ExperimentalStructuredCoroutines::class)
class UserRepository(/* ... */)
```

or opt in for the whole module:

```kotlin
kotlin {
    compilerOptions {
        optIn.add("io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines")
    }
}
```

## Helpers and the rules they support

Rule IDs are defined in [`docs/BEST_PRACTICES_COROUTINES.md`](../docs/BEST_PRACTICES_COROUTINES.md).

| Helper                                                    | Supports                                                    |
|-----------------------------------------------------------|-------------------------------------------------------------|
| `safeCallbackFlow`                                        | INTEROP_002 (`callbackFlow` without `awaitClose`)           |
| `runCatchingCancellable`                                  | CANCEL_003 (swallowed `CancellationException`)              |
| `withCleanup`                                             | CANCEL_004 (suspending cleanup needs `NonCancellable`)      |
| `withTimeoutResult`                                       | CANCEL_006, CANCEL_007 (timeout handling and cleanup)       |
| `parallel`                                                | CONCUR_003, SCOPE_004                                       |
| `supervisedAll`                                           | SCOPE_004, CONCUR_003                                       |
| `concurrentOrdered`                                       | CONCUR_003                                                  |
| `hedged`, `warmIn`                                        | Server-side patterns, not tied to a rule                    |
| `DispatcherProvider`, `DefaultDispatcherProvider`         | DISPATCH_005, KMP_001, TEST_005                             |
| `Dispatchers.ioCompat`                                    | KMP_001 (`Dispatchers.IO` in `commonMain`)                  |
| `instrumented()`, `InstrumentedDispatcher`                | DISPATCH_005 (diagnostic wrapper over injected dispatchers) |
| `StateHolder`                                             | FLOW_010 (do not expose `MutableStateFlow`)                 |
| `EventChannel`                                            | FLOW_011 (replay-0 `SharedFlow` for one-shot events)        |
| `launchInSafely`                                          | FLOW_005 (missing `catch` in a Flow chain)                  |
| `stateInWhileSubscribed`                                  | FLOW_006 (`stateIn` with `Eagerly`)                         |

## Usage

Snippets omit imports; everything lives in `io.github.santimattius.structured.runtime`.

### Interop

`safeCallbackFlow` bridges a callback API and always disposes the returned `DisposableHandle`
exactly once when the collector completes or is cancelled. If `register` throws before returning a
handle, the exception reaches the collector and nothing is disposed.

```kotlin
fun locations(source: LocationSource): Flow<Location> = safeCallbackFlow { channel ->
    val listener = LocationListener { channel.trySend(it) }
    source.addListener(listener)
    DisposableHandle { source.removeListener(listener) }
}
```

### Cancellation

`runCatchingCancellable` behaves like `runCatching` but rethrows `CancellationException`.
`withCleanup` runs `cleanup` inside `withContext(NonCancellable)` after `block` succeeds, fails or is
cancelled; if both fail, the cleanup exception is added as suppressed to the block's exception.

```kotlin
val user = runCatchingCancellable { repository.fetchUser(id) }
    .getOrElse { fallbackUser }

withCleanup(cleanup = { session.close() }) {
    session.send(request)
}
```

### Timeouts

`withTimeoutResult` returns a failed `Result` (holding a `TimeoutCancellationException`) when its own
timeout fires, so the parent scope is not cancelled. An outer cancellation is rethrown, and a timeout
from a nested `withTimeout` propagates unchanged. A `Duration` overload exists.

```kotlin
val profile = withTimeoutResult(2.seconds) { api.loadProfile() }
    .getOrElse { cachedProfile }
```

### Parallelism

All four take tasks as `Iterable<suspend () -> T>`. Declare the list type explicitly so the lambdas
are inferred as suspending.

```kotlin
val tasks = listOf<suspend () -> Item>({ repo.a() }, { repo.b() }, { repo.c() })

val all: List<Item> = tasks.parallel(concurrency = 2)            // fail-fast, input order
val each: List<Result<Item>> = tasks.supervisedAll()              // failures isolated
tasks.concurrentOrdered().collect { response.write(it) }          // cold Flow, input order

val profile = hedged(delay = 50.milliseconds, maxAttempts = 3) { attempt ->
    api.loadProfile(userId)                                       // first success wins
}
```

- `parallel` / `supervisedAll`: `concurrency` defaults to unlimited and must be `> 0`.
- `concurrentOrdered`: starts every task at collection time, emits results in input order as each
  turn is ready, and is fail-fast.
- `hedged`: `maxAttempts` defaults to `2`; `delay` must not be negative. A failing attempt never wins,
  and if all attempts fail the first failure is rethrown with the others suppressed.

### Dispatchers

Inject `DispatcherProvider` instead of hardcoding `Dispatchers.*`. `Dispatchers.ioCompat` is the
`Dispatchers.IO` replacement that compiles in `commonMain`.

```kotlin
class UserRepository(private val dispatchers: DispatcherProvider = DefaultDispatcherProvider) {
    suspend fun load() = withContext(dispatchers.io) { api.fetch() }
}

val io = withContext(Dispatchers.ioCompat) { readFile() }

// Diagnostics only: count dispatches, peak concurrency and queue delay.
val dispatchers = DefaultDispatcherProvider.instrumented()
UserRepository(dispatchers).load()
dispatchers.stats().forEach(::println)
```

`DispatcherProvider.of(dispatcher)` returns a provider whose four dispatchers are the same one.

### State and events

```kotlin
class CounterViewModel {
    private val holder = StateHolder(0)
    val count: StateFlow<Int> = holder.state
    fun increment() = holder.update { it + 1 }   // transform must be side-effect free
}

class LoginViewModel {
    private val _events = EventChannel<LoginEvent>()
    val events: Flow<LoginEvent> = _events.events
    fun onLoggedIn() { _events.trySend(LoginEvent.NavigateHome) }
}
```

### Flow

```kotlin
repository.getItems()
    .map { it.toUiModel() }
    .onEach { _state.value = it }
    .launchInSafely(viewModelScope) { e -> _error.value = e.message }

val items: StateFlow<List<Item>> = repository.observeItems()
    .stateInWhileSubscribed(viewModelScope, initialValue = emptyList())   // stopTimeoutMillis = 5_000

val shared = flow { emit(rpc.load()) }.warmIn(appScope)
shared.collect { render(it) }
shared.collect { audit(it) }   // replays the stored value, no second RPC
```

`launchInSafely` is `catch { onError(it) }.launchIn(scope)`; cancellation does not call `onError`.
`stateInWhileSubscribed` is `stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis,
replayExpirationMillis), initialValue)` with a 5 second default grace period.

## Testing with `:runtime-test`

`TestDispatcherProvider` makes all four dispatchers test dispatchers sharing one
`TestCoroutineScheduler`, so virtual time stays consistent whichever dispatcher work runs on.
`main`, `io` and `default` are `StandardTestDispatcher`s and `unconfined` is an
`UnconfinedTestDispatcher`. With `unconfined = true`, `main`, `io` and `default` are unconfined too.

```kotlin
@Test
fun loadsUser() = runTest {
    val repo = UserRepository(testDispatcherProvider())   // shares runTest's scheduler
    assertEquals(User("1"), repo.load())
}

// Or construct it directly:
val provider = TestDispatcherProvider(scheduler = testScheduler, unconfined = true)
```

Supports DISPATCH_005 and TEST_005. See [`runtime-test/README.md`](../runtime-test/README.md).

## Platform support

Both modules declare the same targets (see `build.gradle.kts`): JVM, JS (browser, Node.js), macOS
(x64, arm64), iOS (x64, arm64, simulator arm64), watchOS (x64, arm64, simulator arm64), tvOS (x64,
arm64, simulator arm64), Linux (x64, arm64), Windows (mingwX64), wasmJs (browser, Node.js) and
wasmWasi (Node.js). No dedicated Android target is declared. The JVM target is built with JVM
toolchain 17.

`Dispatchers.ioCompat` resolves per target:

| Target                  | `Dispatchers.ioCompat`  |
|-------------------------|-------------------------|
| JVM                     | `Dispatchers.IO`        |
| Native (all)            | `Dispatchers.IO`        |
| JS, wasmJs, wasmWasi    | `Dispatchers.Default`   |

## Stability and caveats

- **Experimental, 0.x.** The opt-in gate and the `0.x` version signal that API changes can happen
  without notice. Binary-compatibility checking (`apiCheck` against the checked-in dumps in
  `runtime/api` and `runtime-test/api`) runs as an advisory CI step and does not block; it is
  planned to become blocking once the experimental gate is removed.
- **Instrumentation is diagnostic only.** `InstrumentedDispatcher` is opt-in and not meant for
  production: wrapping `Dispatchers.IO` hides its elasticity and `limitedParallelism` hints.
- **`warmIn` keeps every emitted value in memory** and never restarts upstream, even when all
  collectors leave. Use it for small, finite flows (for example one RPC).
- **`EventChannel` is single-consumer.** Each event goes to exactly one collector; with several
  collectors events are distributed, not broadcast. Use a `MutableSharedFlow` when every subscriber
  must see every event.
- **`DefaultDispatcherProvider.main`** is resolved on each access, so reading it on a platform
  without a main dispatcher (plain JVM) fails.
