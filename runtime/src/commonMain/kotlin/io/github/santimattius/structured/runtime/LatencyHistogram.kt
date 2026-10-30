package io.github.santimattius.structured.runtime

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

private const val UNDERFLOW_BOUND_NANOS = 1_000L
private const val FIRST_EXPONENT = 10
private const val LAST_EXPONENT = 34

// Bucket 0: < 1 us. Buckets 1..25: v < 2^(index + 9). Last bucket: v >= 2^34 (overflow).
private const val FINITE_BUCKETS = LAST_EXPONENT - FIRST_EXPONENT + 2
private const val BUCKETS = FINITE_BUCKETS + 1

private val UPPER_BOUNDS: List<Duration> = buildList {
    add(UNDERFLOW_BOUND_NANOS.nanoseconds)
    for (e in FIRST_EXPONENT..LAST_EXPONENT) add((1L shl e).nanoseconds)
}

/**
 * Immutable view of a latency distribution captured from a histogram.
 *
 * Buckets are logarithmic: an underflow bucket for durations below 1 microsecond, then one bucket
 * per power of two with exclusive upper bounds 2^10 ns up to 2^34 ns (about 17 seconds), then an
 * overflow bucket for everything at or above 2^34 ns. [bucketUpperBounds] lists the finite bounds
 * in order, so it has exactly one element fewer than [bucketCounts]; the last count is the
 * overflow bucket, which has no finite bound.
 *
 * Instances are immutable and thread-safe; they are obtained from [DispatcherMetrics.queueDelay] and
 * [DispatcherMetrics.runTime].
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * Example:
 * ```
 * val p95 = metrics.queueDelay.percentile(0.95)
 * ```
 *
 * @property count number of recorded values.
 * @property max largest recorded value, [Duration.ZERO] when empty.
 * @property bucketCounts number of values per bucket, in bucket order.
 * @property bucketUpperBounds exclusive upper bound of every bucket except the overflow bucket.
 */
@ExperimentalStructuredCoroutines
public class HistogramSnapshot internal constructor(
    public val count: Long,
    public val max: Duration,
    bucketCounts: List<Long>,
) {
    public val bucketCounts: List<Long> = bucketCounts.toList()

    public val bucketUpperBounds: List<Duration> get() = UPPER_BOUNDS

    /**
     * Estimates the value at quantile [p] (0.0 to 1.0) as the UPPER BOUND of the bucket that holds
     * it. Because buckets double in width, the estimate can be up to 2x the real value, but it never
     * exceeds [max], the largest value recorded. When the quantile falls in the overflow bucket the
     * exact [max] is returned instead. An empty histogram returns [Duration.ZERO].
     *
     * Example:
     * ```
     * val median = snapshot.percentile(0.5)
     * ```
     *
     * @param p quantile to estimate, between 0.0 and 1.0 inclusive.
     * @return the upper bound of the bucket containing the quantile (capped at [max]), [max] for the
     * overflow bucket, or [Duration.ZERO] when [count] is zero.
     * @throws IllegalArgumentException if [p] is not within 0.0..1.0.
     */
    public fun percentile(p: Double): Duration {
        require(p in 0.0..1.0) { "p must be within 0.0..1.0, was $p" }
        if (count == 0L) return Duration.ZERO
        val rank = maxOf(1L, kotlin.math.ceil(p * count).toLong())
        var cumulative = 0L
        for (i in bucketCounts.indices) {
            cumulative += bucketCounts[i]
            if (cumulative >= rank) return if (i < UPPER_BOUNDS.size) minOf(UPPER_BOUNDS[i], max) else max
        }
        return max
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is HistogramSnapshot && count == other.count && max == other.max &&
            bucketCounts == other.bucketCounts)

    override fun hashCode(): Int {
        var h = count.hashCode()
        h = 31 * h + max.hashCode()
        h = 31 * h + bucketCounts.hashCode()
        return h
    }

    override fun toString(): String = "HistogramSnapshot(count=$count, max=$max)"
}

/**
 * Lock-free fixed-bucket histogram for nanosecond durations. One [AtomicLong] per bucket, and the
 * maximum is kept with a compare-and-set loop. Recording is allocation free.
 *
 * A [snapshot] taken while other threads record is not a single atomic cut: each counter is read
 * once, so it can miss in-flight values but its [HistogramSnapshot.count] always equals the sum of
 * its buckets.
 */
@OptIn(ExperimentalAtomicApi::class)
@ExperimentalStructuredCoroutines
internal class LatencyHistogram {
    private val buckets = Array(BUCKETS) { AtomicLong(0L) }
    private val max = AtomicLong(0L)

    /** Records [nanos]; negative values are clamped to 0. */
    fun record(nanos: Long) {
        val v = if (nanos < 0L) 0L else nanos
        buckets[indexOf(v)].addAndFetch(1)
        while (true) {
            val current = max.load()
            if (v <= current || max.compareAndSet(current, v)) return
        }
    }

    fun snapshot(): HistogramSnapshot {
        val counts = ArrayList<Long>(BUCKETS)
        var total = 0L
        for (b in buckets) {
            val c = b.load()
            counts.add(c)
            total += c
        }
        return HistogramSnapshot(total, max.load().nanoseconds, counts)
    }

    /** Clears every counter. Not atomic with concurrent [record] calls. */
    fun reset() {
        for (b in buckets) b.store(0L)
        max.store(0L)
    }

    private fun indexOf(v: Long): Int {
        if (v < UNDERFLOW_BOUND_NANOS) return 0
        val bitLength = 64 - v.countLeadingZeroBits()
        return if (bitLength > LAST_EXPONENT) BUCKETS - 1 else bitLength - (FIRST_EXPONENT - 1)
    }
}
