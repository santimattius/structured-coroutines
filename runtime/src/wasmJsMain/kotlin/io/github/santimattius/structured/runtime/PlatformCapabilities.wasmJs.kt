package io.github.santimattius.structured.runtime

@ExperimentalStructuredCoroutines
public actual val platformCapabilities: TraceCapabilities = TraceCapabilities(
    threadIdentity = false,
    systemTraceIntegration = false,
    counters = false,
    flows = false,
)

@ExperimentalStructuredCoroutines
public actual fun platformTraceSink(): TraceSink? = null
