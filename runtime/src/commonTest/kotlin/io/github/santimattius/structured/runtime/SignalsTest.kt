@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class SignalsTest {

    private fun metrics(queueDelayNanos: Long, pending: Long, longestRunNanos: Long): DispatcherMetrics {
        val sink = StatsSink()
        repeat(pending.toInt()) { sink.emit(TraceEvent.Queued("d", 0, null)) }
        sink.emit(TraceEvent.Queued("d", 0, null))
        sink.emit(TraceEvent.Started("d", 0, queueDelayNanos, null, null))
        sink.emit(TraceEvent.Finished("d", 0, longestRunNanos, TraceOutcome.Completed, null, null))
        return sink.metrics("d")!!
    }

    @Test
    fun defaultsAreFiftyAndHundredMilliseconds() {
        val t = SignalThresholds()
        assertEquals(50.milliseconds, t.starvationQueueDelayP95)
        assertEquals(100.milliseconds, t.longRunningSegment)
    }

    @Test
    fun thresholdsValidate() {
        assertFailsWith<IllegalArgumentException> { SignalThresholds(starvationQueueDelayP95 = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { SignalThresholds(longRunningSegment = (-1).milliseconds) }
    }

    @Test
    fun thresholdsEqualityAndToString() {
        assertEquals(SignalThresholds(), SignalThresholds())
        assertEquals(SignalThresholds().hashCode(), SignalThresholds().hashCode())
        assertTrue(SignalThresholds().toString().startsWith("SignalThresholds("))
    }

    @Test
    fun noSignalsForHealthyDispatcher() {
        assertTrue(metrics(1_000, 0, 1_000).signals().isEmpty())
    }

    @Test
    fun longRunningBoundary() {
        val t = SignalThresholds(longRunningSegment = 100.milliseconds)
        assertTrue(metrics(0, 0, 99_999_999).signals(t).isEmpty())
        val s = metrics(0, 0, 100_000_000).signals(t).single()
        assertIs<PoolSignal.LongRunningSegment>(s)
        assertEquals("d", s.label)
        assertEquals(100.milliseconds, s.longestRun)
    }

    @Test
    fun starvationRequiresPendingAndDelay() {
        // 2^26 ns = 67.1 ms bucket bound, above the 50 ms threshold.
        val delay = 60_000_000L
        val starving = metrics(delay, pending = 3, longestRunNanos = 0).signals().single()
        assertIs<PoolSignal.StarvationSuspected>(starving)
        assertEquals(3L, starving.pending)
        assertTrue(starving.queueDelayP95 >= 50.milliseconds)
        // Same delay without a backlog: not starvation.
        assertTrue(metrics(delay, pending = 0, longestRunNanos = 0).signals().isEmpty())
        // Backlog with small delay: not starvation.
        assertTrue(metrics(1_000, pending = 3, longestRunNanos = 0).signals().isEmpty())
    }

    @Test
    fun starvationBoundaryUsesGreaterOrEqualOnBucketUpperBound() {
        val m = metrics(60_000_000, pending = 1, longestRunNanos = 0)
        val p95 = m.queueDelay.percentile(0.95)
        val exact = SignalThresholds(starvationQueueDelayP95 = p95)
        assertTrue(m.signals(exact).any { it is PoolSignal.StarvationSuspected })
        val above = SignalThresholds(starvationQueueDelayP95 = p95 + 1.nanoseconds)
        assertTrue(m.signals(above).none { it is PoolSignal.StarvationSuspected })
    }

    @Test
    fun signalValueSemantics() {
        assertEquals(PoolSignal.LongRunningSegment("a", 1.milliseconds), PoolSignal.LongRunningSegment("a", 1.milliseconds))
        assertEquals(
            PoolSignal.StarvationSuspected("a", 1.milliseconds, 2).hashCode(),
            PoolSignal.StarvationSuspected("a", 1.milliseconds, 2).hashCode(),
        )
    }
}
