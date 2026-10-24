package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Periodically turns the cumulative state of a [StatsSink] into [TraceEvent.PoolSample]s so a pool
 * can be watched over time. One sample is emitted per dispatcher label that [stats] knows.
 *
 * Nothing runs by itself: no coroutine is created until [launchIn] is called, and then it runs in
 * the caller's scope and is cancelled with it. Call [sampleOnce] to sample manually. Samples are
 * emitted at [TraceLevel.Pool] and skipped entirely when [sink] is not enabled for it; feed a sink
 * such as `RingBufferSink(256, TraceLevel.Pool)` and [stats] must be fed by a traced provider.
 *
 * Timestamps are nanoseconds on a monotonic base: the time elapsed since this sampler was created.
 * Only differences between samples of the same sampler are meaningful. A faulty [sink] never
 * propagates an [Exception] to the caller.
 *
 * Example:
 * ```
 * val stats = StatsSink()
 * val ring = RingBufferSink(512, TraceLevel.Pool)
 * val traced = DefaultDispatcherProvider.traced(stats)
 * val sampler = PoolSampler(stats, ring, traced::parallelism)
 * val job = sampler.launchIn(scope, 500.milliseconds)
 * // later
 * ring.snapshot().filterIsInstance<TraceEvent.PoolSample>().signals().forEach(::println)
 * ```
 *
 * @param stats source of the per-label counters.
 * @param sink receives the samples.
 * @param parallelism maximum concurrency of a dispatcher label when known, else null; see
 *   [TracedDispatcherProvider.parallelism].
 */
@ExperimentalStructuredCoroutines
public class PoolSampler internal constructor(
    private val stats: StatsSink,
    private val sink: TraceSink,
    private val parallelism: (String) -> Int?,
    timeSource: TimeSource,
) {
    public constructor(
        stats: StatsSink,
        sink: TraceSink,
        parallelism: (String) -> Int? = { null },
    ) : this(stats, sink, parallelism, TimeSource.Monotonic)

    private val originMark = timeSource.markNow()

    /**
     * Emits one [TraceEvent.PoolSample] per label currently known by the stats sink. Does nothing
     * when the sink is disabled for [TraceLevel.Pool].
     */
    public fun sampleOnce() {
        val enabled = try {
            sink.isEnabled(TraceLevel.Pool)
        } catch (_: Exception) {
            false
        }
        if (!enabled) return
        val now = originMark.elapsedNow().inWholeNanoseconds
        for (m in stats.metrics()) {
            try {
                sink.emit(
                    TraceEvent.PoolSample(
                        dispatcher = m.label,
                        timestampNanos = now,
                        queued = m.queued,
                        started = m.started,
                        finished = m.finished,
                        pending = m.pending,
                        running = m.running,
                        peakRunning = m.peakRunning,
                        parallelism = parallelism(m.label),
                    ),
                )
            } catch (_: Exception) {
                // A faulty sink or parallelism lambda must never break the caller.
            }
        }
    }

    /**
     * Launches a coroutine in [scope] that calls [sampleOnce] and then waits [interval], until the
     * scope or the returned [Job] is cancelled. The first sample is taken immediately. The job
     * never swallows cancellation.
     *
     * @throws IllegalArgumentException if [interval] is not positive.
     */
    public fun launchIn(scope: CoroutineScope, interval: Duration): Job {
        require(interval > Duration.ZERO) { "interval must be > 0, was $interval" }
        return scope.launch {
            while (isActive) {
                sampleOnce()
                delay(interval)
            }
        }
    }
}
