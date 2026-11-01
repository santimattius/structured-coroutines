# Runtime family (`coroutines-runtime/`)

The four Gradle modules that ship or measure the **runtime** side of Structured Coroutines live
here. Gradle project paths are flat (`:runtime`, `:runtime-test`, `:runtime-tracing-jvm`,
`:runtime-benchmark`) and Maven coordinates are unchanged; `settings.gradle.kts` maps each project to
its directory under `coroutines-runtime/`.

## Runtime vs. compile-time

| Side | Modules | What it does |
|------|---------|--------------|
| Compile-time / tooling | `compiler`, `gradle-plugin`, `detekt-rules`, `lint-rules`, `intellij-plugin`, `annotations` | Detects unstructured patterns (compiler errors, static analysis, IDE inspections). Nothing from these ships in your app's runtime path. |
| Runtime (this directory) | `runtime`, `runtime-test`, `runtime-tracing-jvm`, `runtime-benchmark` | Libraries you depend on: the compliant replacements for the flagged patterns, test doubles, and dispatcher tracing. |

The runtime modules are optional. The compile-time tools work without them.

## Module map

| Module | Gradle path | artifactId | Platforms | Purpose | Use it when |
|--------|-------------|------------|-----------|---------|-------------|
| [`runtime`](runtime/README.md) | `:runtime` | `structured-coroutines-runtime` | KMP: JVM, Android, JS, wasmJs, wasmWasi, Apple (macOS, iOS, watchOS, tvOS), Linux, Windows | Compliant helpers (`safeCallbackFlow`, `withTimeoutResult`, `parallel`, `DispatcherProvider`, ...) and the tracing core (`TraceSink`, `TracingDispatcher`, `StatsSink`) | Production code that replaces flagged patterns or wants dispatcher diagnostics |
| [`runtime-test`](runtime-test/README.md) | `:runtime-test` | `structured-coroutines-runtime-test` | KMP: same as `runtime` except Android | `TestDispatcherProvider`, `RecordingSink`, `TraceSinkContract` | Tests of code that takes a `DispatcherProvider`, or of a custom `TraceSink` |
| [`runtime-tracing-jvm`](runtime-tracing-jvm/README.md) | `:runtime-tracing-jvm` | `structured-coroutines-runtime-tracing-jvm` | JVM / desktop (JDK 11+) | `PerfettoTraceSink`, `PerfettoTraceSession` (writes a `.perfetto-trace` file) | You want to inspect dispatch activity in ui.perfetto.dev outside Android |
| [`runtime-benchmark`](runtime-benchmark/README.md) | `:runtime-benchmark` | not published | JVM | Opt-in micro-benchmark of `TracingDispatcher` overhead | Measuring tracing cost; not part of `build`, `check` or CI |

## Dependency graph

```
runtime-test ──────────┐
runtime-tracing-jvm ───┼──► runtime
runtime-benchmark ─────┘
```

- `runtime-test` and `runtime-tracing-jvm` expose `runtime` as an `api` dependency.
- `runtime-benchmark` depends on `runtime` with `implementation`.
- `runtime-tracing-jvm` uses `runtime-test` only in its own tests.
- `runtime` never depends on the other three (it stays free of `kotlinx-coroutines-test` and
  `androidx.tracing`).

## Installation

Group `io.github.santimattius`; all published runtime artifacts share the `RUNTIME_VERSION` from
`gradle.properties` (currently `0.1.0`).

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

```kotlin
// build.gradle.kts (JVM / desktop tracing)
dependencies {
    implementation("io.github.santimattius:structured-coroutines-runtime-tracing-jvm:0.1.0")
}
```

Every API is gated by `@ExperimentalStructuredCoroutines`. Opt in per usage with
`@OptIn(ExperimentalStructuredCoroutines::class)` or for the module with
`optIn.add("io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines")`.

## Quick start

Imports are omitted; the packages are `io.github.santimattius.structured.runtime`, `.runtime.test`
and `.runtime.tracing`.

```kotlin
// runtime: callbacks without leaks, cancellation-safe error handling, bounded timeouts
fun locations(source: LocationSource): Flow<Location> = safeCallbackFlow { channel ->
    val listener = LocationListener { channel.trySend(it) }
    source.addListener(listener)
    DisposableHandle { source.removeListener(listener) }
}

val user = runCatchingCancellable { repository.fetchUser(id) }.getOrElse { fallbackUser }
val profile = withTimeoutResult(2.seconds) { api.loadProfile() }.getOrElse { cachedProfile }
```

```kotlin
// runtime-test: virtual time shared by every dispatcher
@Test
fun loadsUser() = runTest {
    val repo = UserRepository(testDispatcherProvider())
    assertEquals(User("1"), repo.load())
}
```

More helpers (parallelism, dispatchers, `StateHolder`, `EventChannel`, Flow utilities) are in the
[`runtime` README](runtime/README.md#usage).

## Tracing overview

`DispatcherProvider.traced(sink)` wraps `main`, `io` and `default` in a `TracingDispatcher` that
reports `Queued`, `Started` and `Finished` events to a `TraceSink`. It is diagnostic and opt-in; when
the sink is disabled the original block is forwarded untouched.

| Sink | Module | Output |
|------|--------|--------|
| `StatsSink` | `runtime` | Per-dispatcher counters, concurrency, queue-delay and run-time histograms |
| `RingBufferSink`, `LoggingSink`, `CompositeSink` | `runtime` | Last N events, log lines, fan-out |
| `AndroidTraceSink` | `runtime` (Android target) | `android.os.Trace` slices and counters (Perfetto, Macrobenchmark) |
| `PerfettoTraceSink` / `PerfettoTraceSession` | `runtime-tracing-jvm` | `.perfetto-trace` file for JVM/desktop |

```kotlin
val stats = StatsSink()
val dispatchers = DefaultDispatcherProvider.traced(stats)
UserRepository(dispatchers).load()
stats.metrics().forEach(::println)

PerfettoTraceSession.start(File("build/traces")).use { session ->
    val traced = DefaultDispatcherProvider.traced(session.sink)
    withContext(traced.io + CoroutineName("fetch")) { api.fetch() }
}
```

Details, pool sampling and how to read slices: [`runtime` README](runtime/README.md#tracing) and
[`runtime-tracing-jvm` README](runtime-tracing-jvm/README.md).

## Testing utilities overview

- `TestDispatcherProvider` / `TestScope.testDispatcherProvider()` - all dispatchers share one
  `TestCoroutineScheduler`.
- `RecordingSink` - a thread-safe `TraceSink` that records events (`events`, `eventsOf<T>()`,
  `clear()`).
- `TraceSinkContract` - reusable contract checks for any `TraceSink` implementation.

See [`runtime-test` README](runtime-test/README.md).

## Benchmarks

```
./gradlew :runtime-benchmark:tracingBenchmark
./gradlew :runtime-benchmark:tracingBenchmark -PbenchRounds=10 -PbenchOps=2000000 -PbenchWarmup=5
```

Opt-in, nothing asserts on timing. Indicative numbers are in the
[`runtime` README](runtime/README.md#overhead).

## Build, test and API checks

Run from the repository root; project paths do not include `coroutines-runtime`.

| Task | Command |
|------|---------|
| Tests (`runtime`) | `./gradlew :runtime:jvmTest :runtime:jsTest :runtime:linuxX64Test` |
| Tests (`runtime-test`) | `./gradlew :runtime-test:jvmTest :runtime-test:jsTest :runtime-test:linuxX64Test` |
| Tests (`runtime-tracing-jvm`) | `./gradlew :runtime-tracing-jvm:test` |
| Compile JVM | `./gradlew :runtime:compileKotlinJvm :runtime-test:compileKotlinJvm :runtime-tracing-jvm:compileKotlin :runtime-benchmark:compileKotlin` |
| API check | `./gradlew :runtime:apiCheck :runtime-test:apiCheck :runtime-tracing-jvm:apiCheck` |
| API dump | `./gradlew :runtime:apiDump :runtime-test:apiDump :runtime-tracing-jvm:apiDump` |
| API docs | `./gradlew :runtime:dokkaGeneratePublicationHtml` |

`apiCheck` is advisory in CI while `runtime` is on the experimental `0.x` line. Dump files live in
each module's `api/` directory (`runtime/api/jvm/runtime.api` plus `runtime.klib.api`).
`runtime-benchmark` is excluded from API validation. Building `runtime` needs the Android SDK
(platform 36, build-tools 36.0.0); see [CONTRIBUTING.md](../CONTRIBUTING.md).

## Versioning and publishing

- `runtime`, `runtime-test` and `runtime-tracing-jvm` always share `RUNTIME_VERSION`
  (`gradle.properties`), independent of the toolkit's `PROJECT_VERSION`.
- They are published together from a `runtime-v<RUNTIME_VERSION>` release tag by the
  `publish-runtime` workflow, which validates the tag, runs tests and `apiCheck`, then publishes.
- The ordinary `publishToMavenCentral` task publishes only toolkit artifacts. `runtime-benchmark` is
  never published.
- Moving the directories did not change project paths, coordinates or API dumps.

## Module READMEs

- [`runtime/README.md`](runtime/README.md)
- [`runtime-test/README.md`](runtime-test/README.md)
- [`runtime-tracing-jvm/README.md`](runtime-tracing-jvm/README.md)
- [`runtime-benchmark/README.md`](runtime-benchmark/README.md)
