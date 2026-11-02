package io.github.santimattius.structured.runtime

import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/** Maximum length, in UTF-16 code units, that `android.os.Trace.beginSection` accepts. */
private const val MAX_SECTION_NAME = 127

/**
 * Minimal view of a system tracer (on Android, `android.os.Trace`). Exists so the sink logic is
 * plain Kotlin that can be unit-tested on the JVM with a fake.
 */
internal interface SystemTraceApi {
    /** Whether a trace is currently being recorded (always true where that cannot be known). */
    fun isEnabled(): Boolean

    /** Opens a slice on the calling thread. [name] is already sanitized and at most 127 units. */
    fun beginSection(name: String)

    /** Closes the innermost slice opened by [beginSection] on the calling thread. */
    fun endSection()

    /** Publishes a named counter track value. */
    fun setCounter(name: String, value: Long)

    /**
     * Opens an async slice identified by [name] and [cookie], which may be closed from any thread.
     * No-op where the platform has no async sections.
     */
    fun beginAsyncSection(name: String, cookie: Int)

    /** Closes the async slice opened with the same [name] and [cookie]. */
    fun endAsyncSection(name: String, cookie: Int)
}

/**
 * Maps [TraceEvent]s onto a [SystemTraceApi]: `Started` opens a slice, `Finished` closes it and
 * `Queued` only moves the per-dispatcher `pending` counter. `Started` also publishes the segment's
 * queue delay in microseconds as `structured.<label>.queue_delay_us` (last value, only written when
 * it changes) and `structured.<label>.queue_delay_max_us`, the peak delay of the current burst,
 * which returns to zero when the dispatcher has nothing pending or running. Segments that belong to a [TraceSpan] additionally share one async slice named
 * `span:<name>` (cookie = span id), open from the first running segment until the last in-flight
 * segment of that span finishes, so the span stays visible across thread hops. Platform independent; see
 * `AndroidTraceSink` for the public Android binding and its documented limits.
 *
 * `endSection` is only called when this sink opened a section on the same thread (a per-thread
 * open count), so a trace that starts mid-run can never close a slice owned by someone else.
 * Counters (`structured.<label>.pending` and `structured.<label>.running`) are approximate when a
 * trace starts while work is in flight: they are clamped at zero and re-sync as work drains.
 */
@OptIn(ExperimentalAtomicApi::class)
@ExperimentalStructuredCoroutines
internal class SystemTraceSink(
    private val api: SystemTraceApi,
    private val maxLevel: TraceLevel = TraceLevel.Coroutine,
) : TraceSink {

    private class Counters(label: String) {
        val pending = AtomicInt(0)
        val running = AtomicInt(0)
        val pendingName = "structured.$label.pending"
        val runningName = "structured.$label.running"
        val queueDelayName = "structured.$label.queue_delay_us"
        val lastQueueDelayUs = AtomicLong(0L)
        val queueDelayMaxName = "structured.$label.queue_delay_max_us"
        val queueDelayMaxUs = AtomicLong(0L)
    }

    private val counters = AtomicReference<Map<String, Counters>>(emptyMap())

    /** Running segment count per span id; the async slice is open while an entry exists. */
    private val activeSpans = ConcurrentHashMap<Long, Int>()

    /** Sections this sink has open on the current thread. */
    private val open = object : ThreadLocal<IntArray>() {
        override fun initialValue(): IntArray = IntArray(1)
    }

    override fun isEnabled(level: TraceLevel): Boolean =
        level <= maxLevel && try {
            api.isEnabled()
        } catch (_: Exception) {
            false
        }

    @Suppress("REDUNDANT_ELSE_IN_WHEN") // future event subtypes are ignored, not fatal
    override fun emit(event: TraceEvent) {
        try {
            when (event) {
                is TraceEvent.Queued -> {
                    val c = countersFor(event.dispatcher)
                    publish(c.pendingName, c.pending, +1)
                }
                is TraceEvent.Started -> {
                    val c = countersFor(event.dispatcher)
                    publish(c.pendingName, c.pending, -1)
                    publish(c.runningName, c.running, +1)
                    publishQueueDelay(c, event.queueDelayNanos)
                    api.beginSection(sliceName(event.dispatcher, event.coroutineName))
                    open.get()!![0]++
                    event.spanId?.let { spanStarted(it, event.spanName) }
                }
                is TraceEvent.Finished -> {
                    val c = countersFor(event.dispatcher)
                    publish(c.runningName, c.running, -1)
                    resetQueueDelayMaxIfIdle(c)
                    val depth = open.get()!!
                    if (depth[0] > 0) {
                        depth[0]--
                        api.endSection()
                    }
                    event.spanId?.let { spanFinished(it, event.spanName) }
                }
                else -> Unit
            }
        } catch (_: Exception) {
            // A tracing failure must never break the traced work.
        }
    }

    private fun publish(name: String, counter: AtomicInt, delta: Int) {
        while (true) {
            val current = counter.load()
            val next = (current + delta).coerceAtLeast(0)
            if (next == current) return
            if (counter.compareAndSet(current, next)) {
                api.setCounter(name, next.toLong())
                return
            }
        }
    }

    private fun publishQueueDelay(c: Counters, queueDelayNanos: Long) {
        val micros = (queueDelayNanos / 1_000L).coerceAtLeast(0L)
        if (c.lastQueueDelayUs.exchange(micros) != micros) api.setCounter(c.queueDelayName, micros)
        while (true) {
            val max = c.queueDelayMaxUs.load()
            if (micros <= max) return
            if (c.queueDelayMaxUs.compareAndSet(max, micros)) {
                api.setCounter(c.queueDelayMaxName, micros)
                return
            }
        }
    }

    /** Ends the burst: with nothing pending or running, the peak goes back to zero. */
    private fun resetQueueDelayMaxIfIdle(c: Counters) {
        if (c.pending.load() == 0 && c.running.load() == 0 && c.queueDelayMaxUs.exchange(0L) != 0L) {
            api.setCounter(c.queueDelayMaxName, 0L)
        }
    }

    private fun spanStarted(spanId: Long, spanName: String?) {
        val count = activeSpans.merge(spanId, 1) { a, b -> a + b }
        if (count == 1) api.beginAsyncSection(spanSliceName(spanName), spanId.toInt())
    }

    private fun spanFinished(spanId: Long, spanName: String?) {
        var closed = false
        activeSpans.computeIfPresent(spanId) { _, count ->
            if (count <= 1) {
                closed = true
                null
            } else {
                count - 1
            }
        }
        if (closed) api.endAsyncSection(spanSliceName(spanName), spanId.toInt())
    }

    private fun countersFor(label: String): Counters {
        while (true) {
            val current = counters.load()
            current[label]?.let { return it }
            val created = Counters(label)
            if (counters.compareAndSet(current, current + (label to created))) return created
        }
    }

    internal companion object {
        fun spanSliceName(spanName: String?): String = sliceName("span", spanName ?: "unnamed")

        fun sliceName(label: String, coroutineName: String?): String {
            val raw = if (coroutineName == null) label else "$label:$coroutineName"
            val clean = StringBuilder(minOf(raw.length, MAX_SECTION_NAME))
            var i = 0
            while (i < raw.length && clean.length < MAX_SECTION_NAME) {
                val ch = raw[i]
                if (ch.isHighSurrogate() && i + 1 < raw.length && raw[i + 1].isLowSurrogate()) {
                    // Never split a surrogate pair at the cut.
                    if (clean.length + 2 > MAX_SECTION_NAME) break
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
