package io.github.santimattius.structured.runtime

/** Actual for Wasm (WASI): no capability is available, every flag is false. */
@ExperimentalStructuredCoroutines
public actual val platformCapabilities: TraceCapabilities = TraceCapabilities(
    threadIdentity = false,
    systemTraceIntegration = false,
    counters = false,
    flows = false,
)

/** Actual for Wasm (WASI): returns `null`, no system tracer ships with `:runtime`. */
@ExperimentalStructuredCoroutines
public actual fun platformTraceSink(): TraceSink? = null
