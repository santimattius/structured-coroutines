package io.github.santimattius.structured.runtime

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Thresholds used by [signals] to turn [DispatcherMetrics] into [PoolSignal]s. Plain final class
 * with explicit `equals`/`hashCode`/`toString` so options can be added later.
 *
 * Example:
 * ```
 * val strict = SignalThresholds(starvationQueueDelayP95 = 20.milliseconds)
 * ```
 *
 * @property starvationQueueDelayP95 queue-delay p95 at or above which, with a backlog, starvation
 *   is suspected. Must be greater than zero.
 * @property longRunningSegment longest segment at or above which a long-running segment is
 *   reported. Must be greater than zero.
 */
@ExperimentalStructuredCoroutines
public class SignalThresholds(
    public val starvationQueueDelayP95: Duration = 50.milliseconds,
    public val longRunningSegment: Duration = 100.milliseconds,
) {
    init {
        require(starvationQueueDelayP95 > Duration.ZERO) {
            "starvationQueueDelayP95 must be > 0, was $starvationQueueDelayP95"
        }
        require(longRunningSegment > Duration.ZERO) {
            "longRunningSegment must be > 0, was $longRunningSegment"
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SignalThresholds &&
            starvationQueueDelayP95 == other.starvationQueueDelayP95 &&
            longRunningSegment == other.longRunningSegment)

    override fun hashCode(): Int = 31 * starvationQueueDelayP95.hashCode() + longRunningSegment.hashCode()

    override fun toString(): String =
        "SignalThresholds(starvationQueueDelayP95=$starvationQueueDelayP95, longRunningSegment=$longRunningSegment)"
}

/**
 * A heuristic hint derived from [DispatcherMetrics]. Sealed so more signals can be added later.
 * Signals are hints, not proof: they say where to look, not what is wrong.
 *
 * Example:
 * ```
 * when (val signal = metrics.signals().firstOrNull()) {
 *     is PoolSignal.StarvationSuspected -> alert(signal.label)
 *     is PoolSignal.LongRunningSegment -> alert(signal.label)
 *     null -> Unit
 * }
 * ```
 */
@ExperimentalStructuredCoroutines
public sealed interface PoolSignal {
    /** Label of the dispatcher the signal is about. */
    public val label: String

    /**
     * Work is waiting and waited long: [pending] is positive and the queue-delay p95
     * ([queueDelayP95], a bucket upper bound) reached the threshold. [pending] can overcount, see
     * [DispatcherMetrics.pending].
     */
    public class StarvationSuspected(
        override val label: String,
        public val queueDelayP95: Duration,
        public val pending: Long,
    ) : PoolSignal {
        override fun equals(other: Any?): Boolean =
            this === other || (other is StarvationSuspected && label == other.label &&
                queueDelayP95 == other.queueDelayP95 && pending == other.pending)

        override fun hashCode(): Int {
            var h = label.hashCode()
            h = 31 * h + queueDelayP95.hashCode()
            h = 31 * h + pending.hashCode()
            return h
        }

        override fun toString(): String =
            "StarvationSuspected(label=$label, queueDelayP95=$queueDelayP95, pending=$pending)"
    }

    /** A single segment ran for [longestRun], at or above the threshold. */
    public class LongRunningSegment(
        override val label: String,
        public val longestRun: Duration,
    ) : PoolSignal {
        override fun equals(other: Any?): Boolean =
            this === other || (other is LongRunningSegment && label == other.label &&
                longestRun == other.longestRun)

        override fun hashCode(): Int = 31 * label.hashCode() + longestRun.hashCode()

        override fun toString(): String = "LongRunningSegment(label=$label, longestRun=$longestRun)"
    }
}

/**
 * Derives heuristic [PoolSignal]s from these metrics using [thresholds].
 *
 * - [PoolSignal.StarvationSuspected]: `pending > 0` and the queue-delay p95 is at or above
 *   [SignalThresholds.starvationQueueDelayP95].
 * - [PoolSignal.LongRunningSegment]: [DispatcherMetrics.longestRun] is at or above
 *   [SignalThresholds.longRunningSegment]. On a CPU-bound dispatcher a long segment suggests a
 *   blocking call; see `DISPATCH_001` and `DISPATCH_002` in `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * These are heuristics, not proof. The queue-delay p95 is a histogram bucket upper bound (up to 2x
 * the real value). Detecting backlog growth needs periodic samples and arrives with the pool
 * sampler in a later release; this function only looks at one snapshot.
 *
 * Example:
 * ```
 * stats.metrics().flatMap { it.signals() }.forEach(::println)
 * ```
 */
@ExperimentalStructuredCoroutines
public fun DispatcherMetrics.signals(thresholds: SignalThresholds = SignalThresholds()): List<PoolSignal> {
    val result = ArrayList<PoolSignal>(2)
    if (pending > 0) {
        val p95 = queueDelay.percentile(0.95)
        if (p95 >= thresholds.starvationQueueDelayP95) {
            result.add(PoolSignal.StarvationSuspected(label, p95, pending))
        }
    }
    if (longestRun >= thresholds.longRunningSegment) {
        result.add(PoolSignal.LongRunningSegment(label, longestRun))
    }
    return result
}
