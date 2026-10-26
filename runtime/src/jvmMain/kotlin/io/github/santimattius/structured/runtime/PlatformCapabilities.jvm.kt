package io.github.santimattius.structured.runtime

// The JVM has real thread identity, but :runtime ships no JVM system tracer: the Perfetto sink
// lives in the separate :runtime-tracing-jvm artifact, so it does not count here.
@ExperimentalStructuredCoroutines
public actual val platformCapabilities: TraceCapabilities = TraceCapabilities(
    threadIdentity = true,
    systemTraceIntegration = false,
    counters = false,
    flows = false,
)

@ExperimentalStructuredCoroutines
public actual fun platformTraceSink(): TraceSink? = null
