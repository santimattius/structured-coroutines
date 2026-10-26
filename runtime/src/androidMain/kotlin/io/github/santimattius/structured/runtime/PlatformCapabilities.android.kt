package io.github.santimattius.structured.runtime

import android.os.Build

// AndroidTraceSink writes slices (API 18+) and counter tracks (API 29+, so counters is read at
// runtime). It ignores parentSpanId, so flows stay false.
@ExperimentalStructuredCoroutines
public actual val platformCapabilities: TraceCapabilities = TraceCapabilities(
    threadIdentity = true,
    systemTraceIntegration = true,
    counters = Build.VERSION.SDK_INT >= 29,
    flows = false,
)

// AndroidTraceSink() needs no configuration: maxLevel defaults to Coroutine and it stays disabled
// while no trace is being recorded (API 29+).
@ExperimentalStructuredCoroutines
public actual fun platformTraceSink(): TraceSink? = AndroidTraceSink()
