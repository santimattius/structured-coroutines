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

### Tracing

`DispatcherProvider.traced(sink)` wraps `main`, `io` and `default` in a `TracingDispatcher` that
reports `Queued`, `Started` and `Finished` events to a `TraceSink`. Diagnostics only and opt-in:
when the sink is disabled the original block is forwarded untouched.

```kotlin
val stats = StatsSink()
val dispatchers = DefaultDispatcherProvider.traced(stats)
UserRepository(dispatchers).load()
stats.metrics().forEach(::println)
```

Built-in sinks: `StatsSink` (per-dispatcher counters, concurrency and latency histograms),
`RingBufferSink` (last N events), `LoggingSink`, and `CompositeSink` to combine them. On Android,
`AndroidTraceSink` writes to the system trace:

```kotlin
val dispatchers = DefaultDispatcherProvider.traced(AndroidTraceSink())
```

Each run segment becomes a slice named `io` or `io:fetch` (when the coroutine has a
`CoroutineName`) in Perfetto, Android Studio's system trace and Macrobenchmark
`TraceSectionMetric`. Two counter tracks per dispatcher, `structured.<label>.pending` and
`structured.<label>.running`, show pool saturation: a `pending` track that keeps growing while
`running` is flat means the pool is starved. Counters are approximate if a trace starts while work
is in flight (they are clamped at zero and re-sync). Counters need API 29+; below it only slices
are written.

Pool sampling shows how a pool behaves over time instead of only cumulative totals. A
`PoolSampler` turns a `StatsSink` into periodic `TraceEvent.PoolSample`s (counters, `pending`,
`running`, `peakRunning`, `parallelism`). Nothing starts implicitly: you launch it in your own
scope, so it is cancelled with that scope.

```kotlin
val stats = StatsSink()
val ring = RingBufferSink(512, TraceLevel.Pool)
val traced = DefaultDispatcherProvider.traced(stats)
val sampler = PoolSampler(stats, ring, traced::parallelism)
val job = sampler.launchIn(scope, 500.milliseconds)
// later
ring.snapshot().filterIsInstance<TraceEvent.PoolSample>().signals().forEach(::println)
```

`List<PoolSample>.signals()` reports `BacklogGrowing` (`pending` never decreased over the last
samples) and `PoolSaturated` (running at the parallelism limit with work waiting). They are
heuristics. `pending` can overcount with dangling `Queued` events, and sample timestamps are
relative to the sampler's creation.

`TracedDispatcherProvider.parallelism(label)` returns an INFERRED limit, only for `default` and
`io` over `DefaultDispatcherProvider`: `max(2, cpus)` for `default`, and for `io` the
`kotlinx.coroutines.io.parallelism` property when positive, else `max(64, cpus)`. It is `null` on
Native, JS and Wasm (no verified source), for `main` and for any other base provider. The values
can differ if the scheduler is configured otherwise or for `limitedParallelism` views. Default and
IO share one thread pool with independent permits, so this is a per-dispatcher limit, not a
thread count.

Spans link events to the work that caused them. `withTraceSpan(name) { }` opens a `TraceSpan`
in the coroutine context (a plain element, inherited by children); `TracingDispatcher` attaches its
`spanId` and `parentSpanId` to `Queued`, `Started` and `Finished`. Without a span they are `null`.
`hedged`, `parallel`, `supervisedAll` and `concurrentOrdered` open a child span only when the
caller's context already has one, so code that does not use spans is unchanged.

```kotlin
withTraceSpan("checkout") {
    listOf<suspend () -> Item>({ repo.a() }, { repo.b() }).parallel() // runs in child span "parallel"
}
```

Limits:
- Only dispatched segments are visible (not `Dispatchers.Unconfined`, `UNDISPATCHED` first
  segments or same-dispatcher `withContext`); see `TracingDispatcher`.
- `AndroidTraceSink` feeds `android.os.Trace`, which system traces and `TraceSectionMetric` read.
  It does not feed `androidx.tracing`'s in-process trace file; on the JVM and desktop
  `PerfettoTraceSink` does (see below).
- Queue delay is not drawn as a slice; read it from the `pending` counter.
- Keep slice names low-cardinality: use stable `CoroutineName`s, not ids or payloads.

Status: experimental. The tracing API may change, and `AndroidTraceSink` has not been validated on
a device yet.

#### Overhead

Indicative cost `TracingDispatcher.dispatch` adds per call on the JVM, measured with an
inline delegate (no thread hop, no queueing) so only the wrapper is timed. Apple M5 Pro (18 cores),
macOS aarch64, Temurin 17.0.10, `-Xms1g -Xmx1g -XX:+UseSerialGC`, 5 warm-up + 10 measured rounds of
2,000,000 dispatches, single thread, three separate runs:

| Variant | ns/op (median of runs) | bytes/op |
|---|---|---|
| Unwrapped dispatcher (baseline) | 1.6 | 0 |
| Tracing disabled (`TraceSink.None`) | 2.7-2.8 | 0 |
| No-op sink, `Dispatch` level | 47-48 | 144 |
| No-op sink, `Coroutine` level | 51-52 | 168 |
| `StatsSink`, `Dispatch` / `Coroutine` | 52-53 / 57-58 | 144 / 168 |
| `RingBufferSink(1024)`, `Dispatch` / `Coroutine` | 61-62 / 63 | 216 / 240 |

Disabled costs about 1 ns over the baseline (one `isEnabled` call) and allocates nothing; the
block is forwarded untouched. Enabled, each dispatch allocates the wrapper `Runnable` plus the
three events (more for `RingBufferSink`, which also allocates an entry per event) and costs
roughly 45-60 ns. The spread inside a run was about 1-2 ns, and between runs under 2 ns. These
numbers are indicative and machine-dependent: they exclude contention, real thread hops and
sink I/O, and the baseline includes a volatile counter write from the benchmark task. Not a gate:
no test asserts on them. Reproduce with `./gradlew :runtime-benchmark:tracingBenchmark` (opt-in,
not part of `build` or `check`; tune with `-PbenchRounds`, `-PbenchOps`, `-PbenchWarmup`).

On the JVM and desktop, the separate `:runtime-tracing-jvm` artifact provides `PerfettoTraceSink`,
which writes the same slices and counters through `androidx.tracing` into a `.perfetto-trace` file
you can open in [ui.perfetto.dev](https://ui.perfetto.dev). It is a separate artifact so `:runtime`
never depends on `androidx.tracing`. See [`runtime-tracing-jvm/README.md`](../runtime-tracing-jvm/README.md).

```kotlin
PerfettoTraceSession.start(File("build/traces")).use { session ->
    val dispatchers = DefaultDispatcherProvider.traced(session.sink)
    // ... run work ...
}
```

### Writing a platform backend

Everything that differs by platform sits behind two `expect` declarations, so a new target compiles
and works on day one with degraded behavior and gains fidelity when someone writes its `actual`:

```kotlin
public expect val platformCapabilities: TraceCapabilities   // what this target can do
public expect fun platformTraceSink(): TraceSink?           // default system-trace sink, or null
```

To add a backend for a target:

1. Implement a `TraceSink` (see the contract in its KDoc) over the platform tracer.
2. Provide the `actual` of `platformCapabilities` with flags that are true only for what your
   sink really does: `threadIdentity` (run events carry a real `ThreadInfo`), `systemTraceIntegration`
   (`:runtime` ships a sink for the system tracer; backends in separate artifacts do not count),
   `counters` (publishes `pending`/`running` tracks) and `flows` (draws `parentSpanId` links).
3. Return your sink from the `actual` of `platformTraceSink()` if it can be built with no arguments.

The rule: **a missing capability degrades, it never fails.** A sink without thread identity omits
it, one that cannot draw flows ignores `parentSpanId`, and `platformTraceSink()` returns `null`
where nothing ships. Today only Android returns a sink (`AndroidTraceSink`, counters on API 29+);
every other target reports all flags false except the JVM and Android, which have thread identity.

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

`RecordingSink` is a `TraceSink` that records every emitted event in order (thread-safe), for
asserting on tracing. It has `events`, `eventsOf<T>()` and `clear()`; `maxLevel` controls what
`isEnabled` reports (default: every level, `null`: none) to exercise the disabled fast path. It
records whatever it is handed; filtering by level is the caller's job.

```kotlin
val sink = RecordingSink()
val dispatchers = testDispatcherProvider().traced(sink)
// ... run work on dispatchers ...
assertEquals(1, sink.eventsOf<TraceEvent.Finished>().size)
```

`TraceSinkContract` is a reusable check for any `TraceSink`, built-in or third-party: `emit` never
throws (every event type, absent optional fields), tolerates concurrent callers, and `isEnabled`
answers for every level and stays stable. It carries no test-framework annotations, so it works on
every target and framework:

```kotlin
class MyBackendSinkTest : TraceSinkContract() {
    override fun createSink(): TraceSink = MyBackendSink()
    // override fun flush(sink: TraceSink) { ... }  // for sinks that process asynchronously

    @Test
    fun honoursTheSinkContract() = checkAll()
}
```

Supports DISPATCH_005 and TEST_005. See [`runtime-test/README.md`](../runtime-test/README.md).

## Platform support

Both modules declare the same targets (see `build.gradle.kts`): JVM, JS (browser, Node.js), macOS
(x64, arm64), iOS (x64, arm64, simulator arm64), watchOS (x64, arm64, simulator arm64), tvOS (x64,
arm64, simulator arm64), Linux (x64, arm64), Windows (mingwX64), wasmJs (browser, Node.js) and
wasmWasi (Node.js). The JVM target is built with JVM toolchain 17.

`:runtime` additionally declares an **Android** target (`minSdk` 23, `compileSdk` 36, built with the
AGP KMP library plugin `com.android.kotlin.multiplatform.library`). It publishes its own `androidJvm`
Gradle variants next to the plain `jvm` ones, so Android consumers resolve it without extra setup.
`:runtime-test` has no Android target. Building `:runtime` needs the Android SDK (platform 36 and
build-tools 36.0.0; see [CONTRIBUTING.md](../CONTRIBUTING.md)). The Android target also provides
`AndroidTraceSink` (see [Tracing](#tracing)); the other targets have no system-trace sink (the JVM
can write a Perfetto file with `:runtime-tracing-jvm`).

`Dispatchers.ioCompat` resolves per target:

| Target                  | `Dispatchers.ioCompat`  |
|-------------------------|-------------------------|
| JVM                     | `Dispatchers.IO`        |
| Android                 | `Dispatchers.IO`        |
| Native (all)            | `Dispatchers.IO`        |
| JS, wasmJs, wasmWasi    | `Dispatchers.Default`   |

## Stability and caveats

- **Experimental, 0.x.** The opt-in gate and the `0.x` version signal that API changes can happen
  without notice. Binary-compatibility checking (`apiCheck` against the checked-in dumps in
  `runtime/api` and `runtime-test/api`) runs as an advisory CI step and does not block; it is
  planned to become blocking once the experimental gate is removed. **The Android variant has no
  API dump** (Binary Compatibility Validator 0.18.1 does not support the AGP KMP library plugin), so
  its public surface is not guarded by `apiCheck`. It is currently the same source as the JVM target
  (shared `jvmAndAndroid` source set), which the JVM dump does cover.
- **Instrumentation is diagnostic only.** `InstrumentedDispatcher` is opt-in and not meant for
  production: wrapping `Dispatchers.IO` hides its elasticity and `limitedParallelism` hints.
- **`warmIn` keeps every emitted value in memory** and never restarts upstream, even when all
  collectors leave. Use it for small, finite flows (for example one RPC).
- **`EventChannel` is single-consumer.** Each event goes to exactly one collector; with several
  collectors events are distributed, not broadcast. Use a `MutableSharedFlow` when every subscriber
  must see every event.
- **`DefaultDispatcherProvider.main`** is resolved on each access, so reading it on a platform
  without a main dispatcher (plain JVM) fails.
