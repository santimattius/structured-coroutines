@file:OptIn(ExperimentalContextPropagation::class)

package io.github.santimattius.structured.runtime.tracing

import androidx.tracing.ExperimentalContextPropagation
import androidx.tracing.Tracer
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Maximum slice name length, in UTF-16 code units; the same cut `AndroidTraceSink` applies. */
private const val MAX_SLICE_NAME = 127

/** Perfetto category used when none is given. */
private const val DEFAULT_CATEGORY = "structured"

/** Offset that moves span ids into their own range of Perfetto flow ids. */
private const val FLOW_ID_SPACE = 1L shl 62

/**
 * A [TraceSink] that writes dispatch activity through `androidx.tracing`, so a JVM or desktop
 * process produces an in-process Perfetto trace (`.perfetto-trace`) that opens in
 * [ui.perfetto.dev](https://ui.perfetto.dev). It is the desktop counterpart of `AndroidTraceSink`
 * and maps events the same way, so both backends look alike in Perfetto.
 *
 * Mapping: a run segment becomes a slice on the worker thread, named after the dispatcher label
 * (`io`) or, with a `CoroutineName`, `label:name` (`io:fetch`). Names are sanitized (`|`, newline
 * and NUL become spaces) and cut to 127 UTF-16 units. Each dispatcher publishes two counter tracks,
 * `structured.<label>.pending` and `structured.<label>.running`; they are approximate when a trace
 * starts mid-run (clamped at zero, they re-sync as work drains). A slice is only closed on the
 * thread where this sink opened it. Unlike `AndroidTraceSink`, a [TraceEvent.PoolSample] is also
 * written, as the counters `structured.<label>.pool.pending`, `.running`, `.peakRunning` and
 * `.parallelism` (when known).
 *
 * Span links: a run that carries a `TraceSpan` id gets Perfetto flow ids derived from its span id
 * and its parent's, so the UI can draw arrows from a parent span's slices to its children's. A flow
 * links each slice to the previous slice with the same id, so siblings appear chained in start
 * order rather than fanned out from the parent. Runs without a span carry no flow.
 *
 * The tracer is either the one you pass or, when null, [Tracer.global] read at every event. The sink
 * never calls `Tracer.setGlobalTracer` (a library must not claim the process-wide tracer); install
 * one yourself if you want it. With the default stub global tracer every category is off, so
 * [isEnabled] is false and `TracingDispatcher` adds no wrapper. For a ready-made file-backed setup
 * use [PerfettoTraceSession].
 *
 * Limits:
 * - Only dispatched segments are visible; see `TracingDispatcher` for what is not.
 * - Queue delay is not drawn as a slice; read it through the `pending` counter.
 * - Slice names must stay low-cardinality: use stable `CoroutineName`s, never ids or payloads.
 * - The trace file is written asynchronously; call [PerfettoTraceSession.flush] or `close` before
 *   reading it.
 *
 * Experimental, diagnostic and opt-in. Requires JDK 11 or newer (`androidx.tracing` bytecode level).
 *
 * Example:
 * ```
 * val session = PerfettoTraceSession.start(File("build/traces"))
 * val provider = DefaultDispatcherProvider.traced(session.sink)
 * // ... run work ...
 * session.close()
 * ```
 *
 * @param tracer the tracer to write to, or null to use [Tracer.global].
 * @param maxLevel highest [TraceLevel] this sink accepts.
 * @param category the Perfetto category of every slice and counter this sink writes.
 */
@ExperimentalStructuredCoroutines
public class PerfettoTraceSink @JvmOverloads constructor(
    private val tracer: Tracer? = null,
    private val maxLevel: TraceLevel = TraceLevel.Coroutine,
    private val category: String = DEFAULT_CATEGORY,
) : TraceSink {

    private class Counters(label: String) {
        val pending = AtomicInteger(0)
        val running = AtomicInteger(0)
        val pendingName = "structured.$label.pending"
        val runningName = "structured.$label.running"
    }

    private val counters = ConcurrentHashMap<String, Counters>()

    /** Slices this sink has open on the current thread, innermost last. */
    private val open = object : ThreadLocal<ArrayDeque<AutoCloseable>>() {
        override fun initialValue(): ArrayDeque<AutoCloseable> = ArrayDeque()
    }

    private fun current(): Tracer = tracer ?: Tracer.global

    override fun isEnabled(level: TraceLevel): Boolean =
        level <= maxLevel && try {
            current().isCategoryEnabled(category)
        } catch (_: Exception) {
            false
        }

    @Suppress("REDUNDANT_ELSE_IN_WHEN") // future event subtypes are ignored, not fatal
    override fun emit(event: TraceEvent) {
        try {
            val tracer = current()
            when (event) {
                is TraceEvent.Queued -> {
                    val c = countersFor(event.dispatcher)
                    publish(tracer, c.pendingName, c.pending, +1)
                }
                is TraceEvent.Started -> {
                    val c = countersFor(event.dispatcher)
                    publish(tracer, c.pendingName, c.pending, -1)
                    publish(tracer, c.runningName, c.running, +1)
                    open.get()!!.addLast(beginSlice(tracer, event))
                }
                is TraceEvent.Finished -> {
                    val c = countersFor(event.dispatcher)
                    publish(tracer, c.runningName, c.running, -1)
                    open.get()!!.removeLastOrNull()?.close()
                }
                is TraceEvent.PoolSample -> publishPool(tracer, event)
                else -> Unit
            }
        } catch (_: Exception) {
            // A tracing failure must never break the traced work.
        }
    }

    private fun beginSlice(tracer: Tracer, event: TraceEvent.Started): AutoCloseable {
        val spanId = event.spanId
        val parentSpanId = event.parentSpanId
        val token = if (spanId == null) {
            null
        } else {
            val flows = if (parentSpanId == null) {
                listOf(flowId(spanId))
            } else {
                listOf(flowId(parentSpanId), flowId(spanId))
            }
            tracer.tokenForManualPropagation(flows)
        }
        return tracer.beginSection(category, sliceName(event.dispatcher, event.coroutineName), token) {
            addMetadataEntry("queue_delay_ns", event.queueDelayNanos)
            if (spanId != null) addMetadataEntry("span_id", spanId)
            if (parentSpanId != null) addMetadataEntry("parent_span_id", parentSpanId)
        }
    }

    private fun publishPool(tracer: Tracer, sample: TraceEvent.PoolSample) {
        val prefix = "structured.${sample.dispatcher}.pool"
        tracer.counter(category, "$prefix.pending").setValue(sample.pending)
        tracer.counter(category, "$prefix.running").setValue(sample.running.toLong())
        tracer.counter(category, "$prefix.peakRunning").setValue(sample.peakRunning.toLong())
        sample.parallelism?.let { tracer.counter(category, "$prefix.parallelism").setValue(it.toLong()) }
    }

    private fun publish(tracer: Tracer, name: String, counter: AtomicInteger, delta: Int) {
        while (true) {
            val current = counter.get()
            val next = (current + delta).coerceAtLeast(0)
            if (next == current) return
            if (counter.compareAndSet(current, next)) {
                tracer.counter(category, name).setValue(next.toLong())
                return
            }
        }
    }

    private fun countersFor(label: String): Counters = counters.getOrPut(label) { Counters(label) }

    internal companion object {
        /** Flow id for [spanId], moved out of the low range where `androidx.tracing` allocates its own. */
        fun flowId(spanId: Long): Long = spanId + FLOW_ID_SPACE

        fun sliceName(label: String, coroutineName: String?): String {
            val raw = if (coroutineName == null) label else "$label:$coroutineName"
            val clean = StringBuilder(minOf(raw.length, MAX_SLICE_NAME))
            var i = 0
            while (i < raw.length && clean.length < MAX_SLICE_NAME) {
                val ch = raw[i]
                if (ch.isHighSurrogate() && i + 1 < raw.length && raw[i + 1].isLowSurrogate()) {
                    // Never split a surrogate pair at the cut.
                    if (clean.length + 2 > MAX_SLICE_NAME) break
                    clean.append(ch).append(raw[i + 1])
                    i += 2
                    continue
                }
                clean.append(if (ch == '|' || ch == '\n' || ch == '\u0000') ' ' else ch)
                i++
            }
            return clean.toString()
        }
    }
}
