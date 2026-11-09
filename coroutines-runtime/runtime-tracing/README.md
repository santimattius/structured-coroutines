# Structured Coroutines Runtime Tracing

Part of the [runtime family](../README.md) (module map, dependency graph, build and publishing).

A `TraceSink` for [`:runtime`](../runtime/README.md) on the JVM/desktop and on Android:
`PerfettoTraceSink` writes dispatch activity through
[`androidx.tracing`](https://developer.android.com/jetpack/androidx/releases/tracing)
into an in-process Perfetto trace that opens in [ui.perfetto.dev](https://ui.perfetto.dev). On
Android it complements `AndroidTraceSink`: it adds slice arguments (`queue_delay_ns`, `span_name`, ...)
and span flows, which `android.os.Trace` cannot express. It is a separate artifact so `:runtime` consumers never get
`androidx.tracing` transitively.

```kotlin
dependencies {
    implementation("io.github.santimattius:structured-coroutines-runtime-tracing:0.1.0-RC01")
}
```

Like `:runtime`, the API is gated by `@ExperimentalStructuredCoroutines` and versioned on the
independent `0.x` line (`RUNTIME_VERSION`). On the JVM it needs JDK 11 or newer; on Android, minSdk 23.
It pulls `androidx.tracing` 2.0.5 (stable); `tracing-wire` is only used by `PerfettoTraceSession` on the JVM.

> The artifact was `structured-coroutines-runtime-tracing-jvm` in `0.1.0-ALPHA01`. It is now
> `structured-coroutines-runtime-tracing` (KMP: the JVM and Android variants resolve automatically).

## Usage

```kotlin
PerfettoTraceSession.start(File("build/traces")).use { session ->
    val dispatchers = DefaultDispatcherProvider.traced(session.sink)
    withTraceSpan("checkout") {
        withContext(dispatchers.io + CoroutineName("fetch")) { api.fetch() }
    }
} // close() flushes and finishes the file; session.file is the trace
```

Events are written on a background thread, so the file is complete after `flush()` or `close()`.
`flush()` and `close()` may be called concurrently; they are serialized, and `close()` is
idempotent. After close the session sink drops later events. To write into a tracer you already
own, pass it: `PerfettoTraceSink(tracer)`. With no argument the sink reads `Tracer.global` at each
event; it never calls `setGlobalTracer`, and neither does the session (install one yourself if you
want it). With the default stub global tracer every category is off, so the sink reports itself
disabled and `TracingDispatcher` adds no wrapper.

## Android

`PerfettoTraceSink` takes any `androidx.tracing.Tracer`. On Android you own the driver, which needs
a `Context`:

```kotlin
val driver = TraceDriver(
    context = context,
    sink = TraceSink(context), // writes under noBackupFilesDir/perfetto_traces
    // The default only enables categories while a system trace is recording.
    // Use { true } to record into the in-process file at all times.
    isCategoryEnabled = { true },
)
val dispatchers = DefaultDispatcherProvider.traced(PerfettoTraceSink(driver.tracer))
// ... run work, then driver.close() to flush
```

Important differences from `AndroidTraceSink`:
- It records into `androidx.tracing`'s in-process buffer, **not** the system trace buffer. It does
  not show up in a plain Perfetto/Studio system trace; merge it afterwards (`PerfettoCapture` /
  `PerfettoTraceRule`, Benchmark 1.5), or pick `AndroidTraceSink` when you only need slices and
  counters in the system trace.
- With the default `isCategoryEnabled`, nothing is recorded unless a system trace is active, and
  `PerfettoTraceSink.isEnabled` reports false so `TracingDispatcher` adds no wrapper.
- `PerfettoTraceSession` is JVM-only; it does not exist on Android.

## What you see

- A slice per run segment on the worker thread, named `io` or `io:fetch`, with `dispatch_id`,
  `queue_delay_ns`, `span_id`, `parent_span_id` and `span_name` (when present) as slice arguments.
  Names are sanitized and cut to 127 units, like the Android sink.
- Counter tracks `structured.<label>.pending` and `structured.<label>.running`, and, from a
  `PoolSampler`, `structured.<label>.pool.pending`, `.running`, `.peakRunning` and `.parallelism`.
- Flows between the slices of a span and of its child spans (`TraceSpan`). A flow links each slice
  to the previous one with the same id, so siblings appear chained in start order, not fanned out.

Interpret one segment by grouping `Queued`, `Started` and `Finished` events on their non-zero,
process-local `dispatchId`; it is unique per dispatched runnable, while one `TraceSpan` can cover
many such IDs. `Started.queueDelayNanos` (the `queue_delay_ns` argument) is queue time and
`Finished.runTimeNanos` is run time. The `pending` counter is a saturation trend, not an exact
per-segment delay, and can overcount dangling queued events. Span IDs and names connect logical
parent/child work; keep span and slice names low-cardinality and free of request IDs or user data.
Only dispatched segments are visible (see `TracingDispatcher`), and JFR is not supported.

Status: experimental, diagnostic and opt-in. The file output is covered by JVM tests that decode
it, and by an instrumented test (`./gradlew :runtime-tracing:connectedAndroidDeviceTest`, needs a
device or emulator) that decodes the Android file and checks slices, `queue_delay_ns`/`span_name`
arguments and flows. It has not been opened in the Perfetto UI by the test suite.
