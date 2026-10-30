@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TestTimeSource

private class RecordingManualDispatcher : CoroutineDispatcher() {
    val received = ArrayList<Runnable>()
    val queue = ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        received.add(block)
        queue.addLast(block)
    }
    fun runNext() = queue.removeFirst().run()
}

/** Immediate-style: runs inline and reports that no dispatch is needed. */
private class InlineDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean = false
    override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
}

private class ToggleSink(var enabled: Boolean, val inner: RingBufferSink = RingBufferSink(64)) : TraceSink {
    override fun emit(event: TraceEvent) = inner.emit(event)
    override fun isEnabled(level: TraceLevel): Boolean = enabled
}

private class MaxLevelSink(max: TraceLevel) : TraceSink {
    val ring = RingBufferSink(64, max)
    override fun emit(event: TraceEvent) = ring.emit(event)
    override fun isEnabled(level: TraceLevel): Boolean = ring.isEnabled(level)
}

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class TracingDispatcherTest {

    private val clock = TestTimeSource()

    @Test
    fun emitsQueuedStartedFinishedInOrder() {
        val manual = RecordingManualDispatcher()
        val sink = RingBufferSink(16)
        val d = TracingDispatcher(manual, "io", sink, clock)
        d.dispatch(EmptyCoroutineContext, Runnable {})
        assertEquals(listOf("Queued"), sink.snapshot().map { it::class.simpleName })
        manual.runNext()
        val events = sink.snapshot()
        assertIs<TraceEvent.Queued>(events[0])
        assertIs<TraceEvent.Started>(events[1])
        assertIs<TraceEvent.Finished>(events[2])
        assertTrue(events.all { it.dispatcher == "io" })
        assertEquals(TraceOutcome.Completed, (events[2] as TraceEvent.Finished).outcome)
    }

    @Test
    fun queueDelayAndRunTimeAreExact() {
        val manual = RecordingManualDispatcher()
        val sink = RingBufferSink(16)
        val d = TracingDispatcher(manual, "io", sink, clock)
        d.dispatch(EmptyCoroutineContext, Runnable { clock += 70.nanoseconds })
        clock += 30.nanoseconds
        manual.runNext()
        val (q, s, f) = sink.snapshot()
        s as TraceEvent.Started
        f as TraceEvent.Finished
        assertEquals(30L, s.queueDelayNanos)
        assertEquals(70L, f.runTimeNanos)
        assertEquals(30L, s.timestampNanos - q.timestampNanos)
        assertEquals(70L, f.timestampNanos - s.timestampNanos)
    }

    @Test
    fun throwingBlockIsRecordedAndRethrown() {
        val manual = RecordingManualDispatcher()
        val sink = RingBufferSink(16)
        val d = TracingDispatcher(manual, "io", sink, clock)
        val boom = IllegalStateException("boom")
        d.dispatch(EmptyCoroutineContext, Runnable { throw boom })
        val thrown = assertFailsWith<IllegalStateException> { manual.runNext() }
        assertSame(boom, thrown)
        val finished = sink.snapshot().last() as TraceEvent.Finished
        assertSame(boom, assertIs<TraceOutcome.Threw>(finished.outcome).error)
    }

    @Test
    fun disabledSinkForwardsSameRunnableAndEmitsNothing() {
        val manual = RecordingManualDispatcher()
        val toggle = ToggleSink(enabled = false)
        val block = Runnable {}
        TracingDispatcher(manual, "io", toggle, clock).dispatch(EmptyCoroutineContext, block)
        assertSame(block, manual.received.single())
        assertTrue(toggle.inner.snapshot().isEmpty())

        val none = TracingDispatcher(manual, "io", TraceSink.None, clock)
        val block2 = Runnable {}
        none.dispatch(EmptyCoroutineContext, block2)
        assertSame(block2, manual.received[1])
    }

    @Test
    fun dispatchLevelLeavesNameAndThreadNull() {
        val manual = RecordingManualDispatcher()
        val sink = MaxLevelSink(TraceLevel.Dispatch)
        val d = TracingDispatcher(manual, "io", sink, clock)
        d.dispatch(CoroutineName("worker"), Runnable {})
        manual.runNext()
        val (q, s, f) = sink.ring.snapshot()
        assertNull((q as TraceEvent.Queued).coroutineName)
        assertNull((s as TraceEvent.Started).coroutineName)
        assertNull(s.thread)
        assertNull((f as TraceEvent.Finished).coroutineName)
        assertNull(f.thread)
    }

    @Test
    fun coroutineLevelReadsNameFromContext() {
        val manual = RecordingManualDispatcher()
        val sink = RingBufferSink(16)
        val d = TracingDispatcher(manual, "io", sink, clock)
        d.dispatch(CoroutineName("worker"), Runnable {})
        manual.runNext()
        val (q, s, f) = sink.snapshot()
        assertEquals("worker", (q as TraceEvent.Queued).coroutineName)
        assertEquals("worker", (s as TraceEvent.Started).coroutineName)
        assertEquals("worker", (f as TraceEvent.Finished).coroutineName)
    }

    @Test
    fun childInheritsNameAndWithContextOverrides() = runTest {
        val sink = RingBufferSink(64)
        val base = StandardTestDispatcher(testScheduler)
        val d = TracingDispatcher(base, "t", sink, clock)
        // A different wrapper so that withContext really dispatches (same dispatcher stays inline).
        val other = TracingDispatcher(base, "other", sink, clock)
        launch(d + CoroutineName("parent")) {
            launch { }
            withContext(other + CoroutineName("override")) { }
        }
        testScheduler.advanceUntilIdle()
        val queued = sink.snapshot().filterIsInstance<TraceEvent.Queued>()
        assertEquals("parent", queued.first().coroutineName)
        assertTrue(queued.any { it.dispatcher == "t" && it.coroutineName == "parent" && it !== queued.first() },
            "child launch inherits the name: $queued")
        assertTrue(queued.any { it.dispatcher == "other" && it.coroutineName == "override" },
            "withContext override is observed: $queued")
    }

    @Test
    fun faultySinkDoesNotBreakCoroutine() = runTest {
        val faulty = object : TraceSink {
            override fun emit(event: TraceEvent) = throw IllegalStateException("sink down")
        }
        val d = TracingDispatcher(StandardTestDispatcher(testScheduler), "t", faulty, clock)
        var ran = false
        launch(d) { ran = true }
        testScheduler.advanceUntilIdle()
        assertTrue(ran)
    }

    @Test
    fun delayAdvancesVirtualTimeAndResumeIsObserved() = runTest {
        val sink = RingBufferSink(64)
        val d = TracingDispatcher(StandardTestDispatcher(testScheduler), "t", sink, clock)
        val job = launch(d) {
            delay(100)
        }
        runCurrent()
        assertEquals(0L, currentTime)
        assertEquals(1, sink.snapshot().count { it is TraceEvent.Finished })
        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, sink.snapshot().count { it is TraceEvent.Finished })
        advanceTimeBy(1)
        runCurrent()
        assertEquals(100L, currentTime)
        job.join()
        val events = sink.snapshot()
        assertEquals(2, events.count { it is TraceEvent.Queued }, "initial segment + resume")
        assertEquals(2, events.count { it is TraceEvent.Started })
        assertEquals(2, events.count { it is TraceEvent.Finished })
    }

    @Test
    fun noDispatchNeededDelegateEmitsNoQueuedForInlineSegment() = runTest {
        val sink = RingBufferSink(16)
        val d = TracingDispatcher(InlineDispatcher(), "inline", sink, clock)
        var ran = false
        launch(d) { ran = true }
        testScheduler.advanceUntilIdle()
        assertTrue(ran)
        assertTrue(sink.snapshot().isEmpty())
    }

    @Test
    fun isDispatchNeededDelegates() {
        val d = TracingDispatcher(InlineDispatcher(), "i", TraceSink.None, clock)
        assertEquals(false, d.isDispatchNeeded(EmptyCoroutineContext))
        val d2 = TracingDispatcher(RecordingManualDispatcher(), "i", TraceSink.None, clock)
        assertEquals(true, d2.isDispatchNeeded(EmptyCoroutineContext))
    }

    @Test
    fun publicConstructorWorks() = runTest {
        val sink = RingBufferSink(16)
        val d = TracingDispatcher(StandardTestDispatcher(testScheduler), "pub", sink)
        launch(d) { }
        testScheduler.advanceUntilIdle()
        assertEquals(3, sink.snapshot().size)
    }
}

@OptIn(ExperimentalStructuredCoroutines::class)
class TracedDispatcherProviderTest {

    @Test
    fun mainIsNotTouchedUntilRead() {
        val base = TracingProviderBase()
        val traced = base.traced(TraceSink.None)
        traced.io
        traced.default
        traced.unconfined
        assertEquals(0, base.mainReads)
        assertFailsWith<IllegalStateException> { traced.main }
        assertEquals(1, base.mainReads)
    }

    @Test
    fun unconfinedIsReturnedUnwrapped() {
        val base = TracingProviderBase()
        assertSame(base.unconfined, base.traced(TraceSink.None).unconfined)
    }

    @Test
    fun wrapsIoAndDefaultWithLabelsAndExposesSink() = kotlinx.coroutines.test.runTest {
        val sink = RingBufferSink(16)
        val traced = TracingProviderBase().traced(sink)
        assertSame(sink, traced.sink)
        assertTrue(traced.io is TracingDispatcher)
        assertTrue(traced.default is TracingDispatcher)
        assertSame(traced.io, traced.io)
        withContext(traced.io) { }
        withContext(traced.default) { }
        val labels = sink.snapshot().map { it.dispatcher }.toSet()
        assertEquals(setOf("io", "default"), labels)
    }

    @Test
    fun mainIsWrappedWithMainLabel() {
        val manual = RecordingManualDispatcher()
        val sink = RingBufferSink(16)
        val provider = object : DispatcherProvider {
            override val main: CoroutineDispatcher = manual
            override val io: CoroutineDispatcher = manual
            override val default: CoroutineDispatcher = manual
            override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
        }
        val traced = provider.traced(sink)
        traced.main.dispatch(EmptyCoroutineContext, Runnable {})
        assertEquals("main", sink.snapshot().single().dispatcher)
    }
}

@OptIn(ExperimentalStructuredCoroutines::class)
private class TracingProviderBase : DispatcherProvider {
    var mainReads = 0
    override val main: CoroutineDispatcher get() { mainReads++; throw IllegalStateException("no main") }
    override val io: CoroutineDispatcher = Dispatchers.Default
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val unconfined: CoroutineDispatcher = Dispatchers.Unconfined
}
