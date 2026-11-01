package io.github.santimattius.structured.runtime

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.nanoseconds

private const val MAX_TRACKED_THREADS = 4096

/**
 * A [TraceSink] that aggregates events into per-dispatcher [DispatcherMetrics]: counters, running
 * and peak concurrency, queue-delay and run-time histograms, failures and distinct threads.
 *
 * No locks and no extra dependencies. Per-label state lives in an immutable map held in an atomic
 * reference and grown by compare-and-set (labels are few); inside each state every value is an
 * atomic counter, a lock-free [LatencyHistogram], a compare-and-set maximum, or an immutable set
 * replaced by compare-and-set. [emit] is cheap, never blocks and ignores unknown event subtypes.
 * Counters are exact under concurrency; a [metrics] read is not a single atomic cut across fields.
 *
 * Thread tracking only grows for events that carry [ThreadInfo] and stops at 4096 distinct ids.
 *
 * Example:
 * ```
 * val stats = StatsSink()
 * val dispatchers = DefaultDispatcherProvider.traced(stats)
 * // ... run work ...
 * stats.metrics().forEach { println(it) }
 * stats.metrics("io")?.signals()?.forEach(::println)
 * ```
 *
 * Not automatically bounded in time: counters are cumulative until [reset]. Pair it with [PoolSampler]
 * to observe evolution, or read [metrics] and [signals].
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param maxLevel highest [TraceLevel] this sink accepts. Use [TraceLevel.Coroutine] (the default)
 * to also count distinct threads.
 * @see RingBufferSink
 */
@OptIn(ExperimentalAtomicApi::class)
@ExperimentalStructuredCoroutines
public class StatsSink(
    public val maxLevel: TraceLevel = TraceLevel.Coroutine,
) : TraceSink {

    private class State {
        val queued = AtomicLong(0L)
        val started = AtomicLong(0L)
        val finished = AtomicLong(0L)
        val failed = AtomicLong(0L)
        val inFlight = AtomicInt(0)
        val peakRunning = AtomicInt(0)
        val queueDelay = LatencyHistogram()
        val runTime = LatencyHistogram()
        val threads = AtomicReference<Set<Long>>(emptySet())

        fun noteStarted() {
            val now = inFlight.addAndFetch(1)
            while (true) {
                val peak = peakRunning.load()
                if (now <= peak || peakRunning.compareAndSet(peak, now)) return
            }
        }

        fun noteFinished() {
            while (true) {
                val current = inFlight.load()
                if (current <= 0 || inFlight.compareAndSet(current, current - 1)) return
            }
        }

        fun noteThread(id: Long) {
            while (true) {
                val current = threads.load()
                if (id in current || current.size >= MAX_TRACKED_THREADS) return
                if (threads.compareAndSet(current, current + id)) return
            }
        }
    }

    private val states = AtomicReference<Map<String, State>>(emptyMap())

    override fun isEnabled(level: TraceLevel): Boolean = level <= maxLevel

    @Suppress("REDUNDANT_ELSE_IN_WHEN") // kept so future event subtypes are ignored, not fatal
    override fun emit(event: TraceEvent) {
        when (event) {
            is TraceEvent.Queued -> stateFor(event.dispatcher).queued.addAndFetch(1)
            is TraceEvent.Started -> {
                val s = stateFor(event.dispatcher)
                s.started.addAndFetch(1)
                s.queueDelay.record(event.queueDelayNanos)
                s.noteStarted()
                event.thread?.let { s.noteThread(it.id) }
            }
            is TraceEvent.Finished -> {
                val s = stateFor(event.dispatcher)
                s.finished.addAndFetch(1)
                s.runTime.record(event.runTimeNanos)
                s.noteFinished()
                if (event.outcome is TraceOutcome.Threw) s.failed.addAndFetch(1)
            }
            else -> Unit // Unknown subtypes from future releases are ignored.
        }
    }

    /**
     * Returns the metrics of every observed dispatcher label, sorted by label. Empty when nothing
     * was observed.
     */
    public fun metrics(): List<DispatcherMetrics> =
        states.load().entries.sortedBy { it.key }.map { (label, s) -> snapshotOf(label, s) }

    /**
     * Returns the metrics of [label], or null when no event with that label was observed.
     *
     * @param label dispatcher label, such as `"io"`.
     */
    public fun metrics(label: String): DispatcherMetrics? =
        states.load()[label]?.let { snapshotOf(label, it) }

    /**
     * Forgets everything observed so far. Not atomic with concurrent [emit] calls: events emitted
     * while it runs may be lost or survive it, like [RingBufferSink.clear].
     */
    public fun reset() {
        states.store(emptyMap())
    }

    private fun stateFor(label: String): State {
        while (true) {
            val current = states.load()
            current[label]?.let { return it }
            val created = State()
            if (states.compareAndSet(current, current + (label to created))) return created
        }
    }

    private fun snapshotOf(label: String, s: State): DispatcherMetrics {
        val queued = s.queued.load()
        val started = s.started.load()
        val finished = s.finished.load()
        val runTime = s.runTime.snapshot()
        return DispatcherMetrics(
            label = label,
            queued = queued,
            started = started,
            finished = finished,
            running = (started - finished).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
            peakRunning = s.peakRunning.load(),
            pending = maxOf(0L, queued - started),
            queueDelay = s.queueDelay.snapshot(),
            runTime = runTime,
            longestRun = runTime.max,
            failed = s.failed.load(),
            threadsObserved = s.threads.load().size,
        )
    }
}
