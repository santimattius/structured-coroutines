@file:OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

private fun queued(label: String = "io", at: Long = 0) = TraceEvent.Queued(label, at, null)

private fun started(label: String = "io", delay: Long = 0, thread: ThreadInfo? = null, at: Long = 0) =
    TraceEvent.Started(label, at, delay, null, thread)

private fun finished(
    label: String = "io",
    run: Long = 0,
    outcome: TraceOutcome = TraceOutcome.Completed,
    thread: ThreadInfo? = null,
) = TraceEvent.Finished(label, 0, run, outcome, null, thread)

class StatsSinkTest {

    @Test
    fun countsQueuedStartedFinishedAndFailed() {
        val sink = StatsSink()
        repeat(3) { sink.emit(queued()) }
        repeat(2) { sink.emit(started()) }
        sink.emit(finished())
        sink.emit(finished(outcome = TraceOutcome.Threw(IllegalStateException("x"))))
        val m = sink.metrics("io")!!
        assertEquals("io", m.label)
        assertEquals(3L, m.queued)
        assertEquals(2L, m.started)
        assertEquals(2L, m.finished)
        assertEquals(1L, m.failed)
        assertEquals(0, m.running)
        assertEquals(1L, m.pending)
    }

    @Test
    fun runningAndPeakRunning() {
        val sink = StatsSink()
        repeat(3) { sink.emit(started()) }
        assertEquals(3, sink.metrics("io")!!.running)
        sink.emit(finished())
        sink.emit(finished())
        val m = sink.metrics("io")!!
        assertEquals(1, m.running)
        assertEquals(3, m.peakRunning)
        sink.emit(started())
        assertEquals(3, sink.metrics("io")!!.peakRunning)
    }

    @Test
    fun runningNeverNegative() {
        val sink = StatsSink()
        sink.emit(finished())
        val m = sink.metrics("io")!!
        assertEquals(0, m.running)
        assertEquals(0, m.peakRunning)
    }

    @Test
    fun danglingQueuedLeavesPending() {
        val sink = StatsSink()
        repeat(4) { sink.emit(queued()) }
        sink.emit(started())
        sink.emit(finished())
        assertEquals(3L, sink.metrics("io")!!.pending)
    }

    @Test
    fun pendingNeverNegative() {
        val sink = StatsSink()
        sink.emit(started())
        assertEquals(0L, sink.metrics("io")!!.pending)
    }

    @Test
    fun queueDelayAndRunTimeHistogramsAndLongestRun() {
        val sink = StatsSink()
        sink.emit(started(delay = 2_000))
        sink.emit(started(delay = 3_000_000))
        sink.emit(finished(run = 10_000))
        sink.emit(finished(run = 7_000_000))
        val m = sink.metrics("io")!!
        assertEquals(2L, m.queueDelay.count)
        assertEquals(3_000_000.nanoseconds, m.queueDelay.max)
        assertEquals(2L, m.runTime.count)
        assertEquals(7_000_000.nanoseconds, m.longestRun)
        assertEquals(7_000_000.nanoseconds, m.runTime.max)
    }

    @Test
    fun distinctThreadIdsAreCounted() {
        val sink = StatsSink()
        sink.emit(started(thread = ThreadInfo(1, "a")))
        sink.emit(started(thread = ThreadInfo(1, "a")))
        sink.emit(started(thread = ThreadInfo(2, "b")))
        sink.emit(started(thread = null))
        sink.emit(finished(thread = ThreadInfo(3, "c")))
        assertEquals(2, sink.metrics("io")!!.threadsObserved)
    }

    @Test
    fun threadsObservedIsZeroWithoutThreadInfo() {
        val sink = StatsSink()
        sink.emit(started())
        assertEquals(0, sink.metrics("io")!!.threadsObserved)
    }

    @Test
    fun threadTrackingIsCapped() {
        val sink = StatsSink()
        repeat(5_000) { sink.emit(started(thread = ThreadInfo(it.toLong(), "t$it"))) }
        assertEquals(4096, sink.metrics("io")!!.threadsObserved)
        assertEquals(5_000L, sink.metrics("io")!!.started)
    }

    @Test
    fun labelsAreIsolatedAndSorted() {
        val sink = StatsSink()
        sink.emit(queued("io"))
        sink.emit(queued("default"))
        sink.emit(queued("default"))
        sink.emit(queued("main"))
        assertEquals(listOf("default", "io", "main"), sink.metrics().map { it.label })
        assertEquals(2L, sink.metrics("default")!!.queued)
        assertEquals(1L, sink.metrics("io")!!.queued)
    }

    @Test
    fun unknownLabelIsNull() {
        val sink = StatsSink()
        assertNull(sink.metrics("nope"))
        assertTrue(sink.metrics().isEmpty())
    }

    @Test
    fun levelGating() {
        val dispatch = StatsSink(maxLevel = TraceLevel.Dispatch)
        assertTrue(dispatch.isEnabled(TraceLevel.Dispatch))
        assertFalse(dispatch.isEnabled(TraceLevel.Coroutine))
        val default = StatsSink()
        assertTrue(default.isEnabled(TraceLevel.Coroutine))
        assertFalse(default.isEnabled(TraceLevel.Pool))
    }

    @Test
    fun resetClearsAllLabels() {
        val sink = StatsSink()
        sink.emit(queued())
        sink.emit(started())
        sink.reset()
        assertTrue(sink.metrics().isEmpty())
        sink.emit(queued())
        assertEquals(1L, sink.metrics("io")!!.queued)
        assertEquals(0L, sink.metrics("io")!!.started)
    }

    @Test
    fun snapshotDoesNotChangeAfterMoreEvents() {
        val sink = StatsSink()
        sink.emit(queued())
        val before = sink.metrics("io")!!
        sink.emit(queued())
        assertEquals(1L, before.queued)
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun tracingDispatcherFeedsStatsSink() = runTest {
        val clock = TestTimeSource()
        val sink = StatsSink()
        val test = StandardTestDispatcher(testScheduler)
        val d = TracingDispatcher(test, "work", sink, clock)
        val scope = kotlinx.coroutines.CoroutineScope(d)
        repeat(3) { scope.launch { clock += 1.milliseconds } }
        runCurrent()
        val m = sink.metrics("work")!!
        assertEquals(3L, m.queued)
        assertEquals(3L, m.started)
        assertEquals(3L, m.finished)
        assertEquals(0L, m.pending)
        assertEquals(1.milliseconds, m.longestRun)
    }

    private class ManualDispatcher : CoroutineDispatcher() {
        val queue = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }
        fun runNext() = queue.removeFirst().run()
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun starvationScenarioYieldsStarvationSuspected() {
        val clock = TestTimeSource()
        val sink = StatsSink()
        val manual = ManualDispatcher()
        val d = TracingDispatcher(manual, "cpu", sink, clock)
        // One long task, with many tasks queued behind it.
        d.dispatch(EmptyCoroutineContext, Runnable { clock += 500.milliseconds })
        repeat(10) { d.dispatch(EmptyCoroutineContext, Runnable {}) }
        manual.runNext() // the long one runs while 10 wait
        val mid = sink.metrics("cpu")!!
        assertEquals(10L, mid.pending)
        repeat(10) { manual.runNext() }
        val m = sink.metrics("cpu")!!
        val signals = m.signals()
        assertTrue(signals.any { it is PoolSignal.LongRunningSegment }, "$signals")
        assertEquals(0L, m.pending)
        // Pending is zero after drain, so starvation is not suspected any more...
        assertTrue(signals.none { it is PoolSignal.StarvationSuspected })
        // ...but it was while the backlog existed: evaluate on the mid-run snapshot with its delays.
        val sink2 = StatsSink()
        val d2 = TracingDispatcher(manual, "cpu", sink2, clock)
        d2.dispatch(EmptyCoroutineContext, Runnable { clock += 500.milliseconds })
        repeat(10) { d2.dispatch(EmptyCoroutineContext, Runnable {}) }
        manual.runNext()
        manual.runNext() // first waiter started after 500 ms of delay, 9 still pending
        val starving = sink2.metrics("cpu")!!.signals()
        val s = starving.filterIsInstance<PoolSignal.StarvationSuspected>().single()
        assertEquals("cpu", s.label)
        assertEquals(9L, s.pending)
        assertTrue(s.queueDelayP95 >= 50.milliseconds)
        manual.queue.clear()
    }
}
