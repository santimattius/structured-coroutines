package io.github.santimattius.structured.runtime

import android.os.Build
import android.os.Trace

/** Thin binding of [SystemTraceApi] to `android.os.Trace`, guarded by API level. */
internal object AndroidSystemTrace : SystemTraceApi {
    // Trace.isEnabled() and Trace.setCounter() are API 29; begin/endSection are API 18 (minSdk is 23).
    override fun isEnabled(): Boolean =
        if (Build.VERSION.SDK_INT >= 29) Trace.isEnabled() else true

    override fun beginSection(name: String) = Trace.beginSection(name)

    override fun endSection() = Trace.endSection()

    override fun setCounter(name: String, value: Long) {
        if (Build.VERSION.SDK_INT >= 29) Trace.setCounter(name, value)
    }
}

/**
 * A [TraceSink] that writes dispatch activity to the Android system trace (`android.os.Trace`), so
 * it shows up in Perfetto, Android Studio's system trace and Macrobenchmark `TraceSectionMetric`.
 *
 * Mapping: a run segment becomes a slice on the worker thread, named after the dispatcher label
 * (`io`) or, when the coroutine has a `CoroutineName`, `label:name` (`io:fetch`). Names are
 * sanitized (`|`, newline and NUL become spaces) and cut to 127 UTF-16 units, the platform limit.
 * Each dispatcher also publishes two counter tracks, `structured.<label>.pending` (queued, not yet
 * started) and `structured.<label>.running`, which show pool saturation. Counters are approximate
 * when a trace starts while work is in flight: they are clamped at zero and re-sync as work
 * drains. A slice is only closed on the thread that opened it by this sink, so starting a trace
 * mid-run never closes another module's slice.
 *
 * When no trace is being recorded (API 29+), [isEnabled] is false and `TracingDispatcher` forwards
 * the original block with no wrapper. Below API 29 the recording state is unknown, so the sink is
 * always enabled and the counters are not published.
 *
 * Limits:
 * - Only dispatched segments are visible; see [TracingDispatcher] for what is not.
 * - It writes to `android.os.Trace`, which is what system traces and `TraceSectionMetric` read.
 *   It does not feed `androidx.tracing`'s in-process trace file.
 * - Queue delay is not drawn as a slice; read it through the `pending` counter.
 * - Slice names must stay low-cardinality: use stable `CoroutineName`s, never ids or payloads.
 *
 * Experimental, diagnostic and opt-in. Not verified here on a device.
 *
 * Platform availability: Android target only (`android.os.Trace`). It is the Android-specific
 * `actual` behind [platformTraceSink]; on the JVM use the `:runtime-tracing-jvm` artifact instead.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * Example:
 * ```
 * val provider = DefaultDispatcherProvider.traced(AndroidTraceSink())
 * ```
 *
 * @constructor Creates a sink that writes to `android.os.Trace`.
 * @param maxLevel highest [TraceLevel] this sink accepts.
 * @see platformTraceSink
 * @see TracingDispatcher
 */
@ExperimentalStructuredCoroutines
public class AndroidTraceSink(
    maxLevel: TraceLevel = TraceLevel.Coroutine,
) : TraceSink {
    private val delegate = SystemTraceSink(AndroidSystemTrace, maxLevel)

    override fun emit(event: TraceEvent) {
        delegate.emit(event)
    }

    override fun isEnabled(level: TraceLevel): Boolean = delegate.isEnabled(level)
}
