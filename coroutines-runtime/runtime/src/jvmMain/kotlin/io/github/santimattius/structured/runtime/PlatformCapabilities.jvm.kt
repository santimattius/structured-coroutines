package io.github.santimattius.structured.runtime

// The JVM has real thread identity, but :runtime ships no JVM system tracer: the Perfetto sink
// lives in the separate :runtime-tracing-jvm artifact, so it does not count here.
/** Actual for the JVM: only [TraceCapabilities.threadIdentity] is true. The Perfetto sink lives in the separate `:runtime-tracing-jvm` artifact. */
@ExperimentalStructuredCoroutines
public actual val platformCapabilities: TraceCapabilities = TraceCapabilities(
    threadIdentity = true,
    systemTraceIntegration = false,
    counters = false,
    flows = false,
)

/** Actual for the JVM: returns `null`; use `PerfettoTraceSink` from `:runtime-tracing-jvm` for system-trace output. */
@ExperimentalStructuredCoroutines
public actual fun platformTraceSink(): TraceSink? = null
