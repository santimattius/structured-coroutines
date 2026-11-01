package io.github.santimattius.structured.runtime

import kotlin.time.Duration

/**
 * Immutable snapshot of what a [StatsSink] observed for one dispatcher label. A plain final class
 * with explicit `equals`/`hashCode`/`toString` so fields can be added later without breaking
 * binary compatibility.
 *
 * Counters are cumulative since the sink was created or last reset. [pending] is derived from
 * `queued - started`: a dispatcher that swallows blocks (for example a wrapped
 * `Dispatchers.Unconfined` on `yield()`) leaves dangling [TraceEvent.Queued] events, so [pending]
 * can overcount.
 *
 * Instances are produced by [StatsSink.metrics]; the constructor is internal.
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * Example:
 * ```
 * val io = stats.metrics("io")
 * println("running=${io?.running} pending=${io?.pending} p95=${io?.queueDelay?.percentile(0.95)}")
 * ```
 *
 * @property label dispatcher label the events carried.
 * @property queued number of [TraceEvent.Queued] events.
 * @property started number of [TraceEvent.Started] events.
 * @property finished number of [TraceEvent.Finished] events.
 * @property running segments currently running: `started - finished`, never negative.
 * @property peakRunning highest number of segments observed running at the same time.
 * @property pending segments queued and not yet started: `max(0, queued - started)`.
 * @property queueDelay distribution of the time blocks waited before starting.
 * @property runTime distribution of how long each segment ran.
 * @property longestRun longest single segment observed.
 * @property failed number of segments that finished by throwing (including cancellation).
 * @property threadsObserved distinct thread ids seen on [TraceEvent.Started]. Only populated when
 *   events carry thread identity, that is with [TraceLevel.Coroutine] on a platform that has
 *   threads. Tracking stops growing at 4096 distinct ids, so the value never exceeds 4096.
 */
@ExperimentalStructuredCoroutines
public class DispatcherMetrics internal constructor(
    public val label: String,
    public val queued: Long,
    public val started: Long,
    public val finished: Long,
    public val running: Int,
    public val peakRunning: Int,
    public val pending: Long,
    public val queueDelay: HistogramSnapshot,
    public val runTime: HistogramSnapshot,
    public val longestRun: Duration,
    public val failed: Long,
    public val threadsObserved: Int,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is DispatcherMetrics && label == other.label && queued == other.queued &&
            started == other.started && finished == other.finished && running == other.running &&
            peakRunning == other.peakRunning && pending == other.pending &&
            queueDelay == other.queueDelay && runTime == other.runTime &&
            longestRun == other.longestRun && failed == other.failed &&
            threadsObserved == other.threadsObserved)

    override fun hashCode(): Int {
        var h = label.hashCode()
        h = 31 * h + queued.hashCode()
        h = 31 * h + started.hashCode()
        h = 31 * h + finished.hashCode()
        h = 31 * h + running
        h = 31 * h + peakRunning
        h = 31 * h + pending.hashCode()
        h = 31 * h + queueDelay.hashCode()
        h = 31 * h + runTime.hashCode()
        h = 31 * h + longestRun.hashCode()
        h = 31 * h + failed.hashCode()
        h = 31 * h + threadsObserved
        return h
    }

    override fun toString(): String =
        "DispatcherMetrics(label=$label, queued=$queued, started=$started, finished=$finished, " +
            "running=$running, peakRunning=$peakRunning, pending=$pending, longestRun=$longestRun, " +
            "failed=$failed, threadsObserved=$threadsObserved)"
}
