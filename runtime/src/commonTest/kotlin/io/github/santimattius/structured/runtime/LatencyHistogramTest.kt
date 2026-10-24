@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

class LatencyHistogramTest {

    private fun bucketOf(nanos: Long): Int {
        val h = LatencyHistogram()
        h.record(nanos)
        return h.snapshot().bucketCounts.indexOfFirst { it == 1L }
    }

    @Test
    fun layoutIsUnderflowTwentyFivePowersOfTwoAndOverflow() {
        val s = LatencyHistogram().snapshot()
        assertEquals(27, s.bucketCounts.size)
        assertEquals(26, s.bucketUpperBounds.size)
        assertEquals(1.microseconds, s.bucketUpperBounds.first())
        assertEquals((1L shl 10).nanoseconds, s.bucketUpperBounds[1])
        assertEquals((1L shl 34).nanoseconds, s.bucketUpperBounds.last())
    }

    @Test
    fun belowOneMicrosecondGoesToUnderflow() {
        assertEquals(0, bucketOf(0))
        assertEquals(0, bucketOf(999))
    }

    @Test
    fun oneMicrosecondLeavesUnderflow() {
        assertEquals(1, bucketOf(1_000))
        assertEquals(1, bucketOf((1L shl 10) - 1))
    }

    @Test
    fun powerOfTwoBoundaryStartsNextBucket() {
        assertEquals(2, bucketOf(1L shl 10))
        assertEquals(2, bucketOf((1L shl 11) - 1))
        assertEquals(3, bucketOf(1L shl 11))
        assertEquals(25, bucketOf((1L shl 34) - 1))
    }

    @Test
    fun atOrAbove2Pow34GoesToOverflow() {
        assertEquals(26, bucketOf(1L shl 34))
        assertEquals(26, bucketOf(Long.MAX_VALUE))
    }

    @Test
    fun negativeIsClampedToZero() {
        val h = LatencyHistogram()
        h.record(-5)
        val s = h.snapshot()
        assertEquals(1L, s.count)
        assertEquals(1L, s.bucketCounts[0])
        assertEquals(Duration.ZERO, s.max)
    }

    @Test
    fun emptyHistogram() {
        val s = LatencyHistogram().snapshot()
        assertEquals(0L, s.count)
        assertEquals(Duration.ZERO, s.max)
        assertEquals(Duration.ZERO, s.percentile(0.5))
        assertEquals(Duration.ZERO, s.percentile(1.0))
    }

    @Test
    fun maxTracksLargestAndCountSumsBuckets() {
        val h = LatencyHistogram()
        listOf(5L, 2_000_000L, 70_000L).forEach(h::record)
        val s = h.snapshot()
        assertEquals(3L, s.count)
        assertEquals(2_000_000.nanoseconds, s.max)
        assertEquals(3L, s.bucketCounts.sum())
    }

    @Test
    fun percentileReturnsUpperBoundOfBucketHoldingTheQuantile() {
        val h = LatencyHistogram()
        repeat(90) { h.record(2_000) }          // bucket with bound 2^11 = 2048
        repeat(10) { h.record(1_000_000) }      // bucket with bound 2^20
        val s = h.snapshot()
        assertEquals((1L shl 11).nanoseconds, s.percentile(0.5))
        assertEquals((1L shl 11).nanoseconds, s.percentile(0.9))
        assertEquals((1L shl 20).nanoseconds, s.percentile(0.95))
        assertEquals((1L shl 20).nanoseconds, s.percentile(1.0))
        assertEquals((1L shl 11).nanoseconds, s.percentile(0.0))
    }

    @Test
    fun overflowBucketPercentileReturnsMax() {
        val h = LatencyHistogram()
        h.record(1L shl 40)
        assertEquals((1L shl 40).nanoseconds, h.snapshot().percentile(0.99))
    }

    @Test
    fun percentileValidatesRange() {
        val s = LatencyHistogram().snapshot()
        assertFailsWith<IllegalArgumentException> { s.percentile(-0.01) }
        assertFailsWith<IllegalArgumentException> { s.percentile(1.01) }
        assertFailsWith<IllegalArgumentException> { s.percentile(Double.NaN) }
    }

    @Test
    fun resetClearsEverything() {
        val h = LatencyHistogram()
        h.record(5_000)
        h.reset()
        val s = h.snapshot()
        assertEquals(0L, s.count)
        assertEquals(Duration.ZERO, s.max)
    }

    @Test
    fun snapshotIsImmutable() {
        val h = LatencyHistogram()
        h.record(5_000)
        val before = h.snapshot()
        h.record(5_000)
        assertEquals(1L, before.count)
        assertEquals(2L, h.snapshot().count)
    }
}
