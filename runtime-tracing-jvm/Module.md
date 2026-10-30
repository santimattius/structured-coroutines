# Module structured-coroutines-runtime-tracing-jvm

JVM-only Perfetto backend for the runtime tracing API. It writes dispatch activity through
`androidx.tracing` into an in-process `.perfetto-trace` file that opens in ui.perfetto.dev. It is a
separate artifact so `structured-coroutines-runtime` consumers do not receive `androidx.tracing`
transitively. On Android use `AndroidTraceSink` from the runtime module instead.

# Package io.github.santimattius.structured.runtime.tracing

`PerfettoTraceSink`, a `TraceSink` that maps trace events to slices, counters and flows, and
`PerfettoTraceSession`, a ready-made file-backed setup. Experimental, diagnostic and opt-in.
