package io.github.santimattius.structured.runtime

/**
 * Identity of the thread running the caller, or null on platforms without a meaningful thread
 * notion (native, JS, Wasm). Allocates a [ThreadInfo] per call, so call it only when needed.
 */
@OptIn(ExperimentalStructuredCoroutines::class)
internal expect fun currentThreadInfo(): ThreadInfo?
