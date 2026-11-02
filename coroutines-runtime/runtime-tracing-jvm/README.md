# Structured Coroutines Runtime Tracing (JVM)

Part of the [runtime family](../README.md) (module map, dependency graph, build and publishing).

A JVM/desktop `TraceSink` for [`:runtime`](../runtime/README.md): `PerfettoTraceSink` writes
dispatch activity through [`androidx.tracing`](https://developer.android.com/jetpack/androidx/releases/tracing)
into an in-process Perfetto trace (`.perfetto-trace`) that opens in
[ui.perfetto.dev](https://ui.perfetto.dev). It is the desktop counterpart of `AndroidTraceSink` and
maps events the same way. It is a separate artifact so `:runtime` consumers never get
`androidx.tracing` transitively.

```kotlin
dependencies {
    implementation("io.github.santimattius:structured-coroutines-runtime-tracing-jvm:0.1.0-ALPHA01")
}
```

Like `:runtime`, the API is gated by `@ExperimentalStructuredCoroutines` and versioned on the
independent `0.x` line (`RUNTIME_VERSION`). It needs JDK 11 or newer and pulls `androidx.tracing`
2.0.5 (stable) and its `tracing-wire` serializer.

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
it; it has not been opened in the Perfetto UI by the test suite.
