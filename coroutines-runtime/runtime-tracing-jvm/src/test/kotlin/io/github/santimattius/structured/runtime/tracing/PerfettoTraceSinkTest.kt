@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.tracing

import androidx.tracing.DelicateTracingApi
import androidx.tracing.Tracer
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.ThreadInfo
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceOutcome
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PerfettoTraceSinkTest {

    private val thread = ThreadInfo(1L, "worker")

    private fun started(label: String = "io", name: String? = null, spanId: Long? = null, parentSpanId: Long? = null) =
        TraceEvent.Started(label, 1L, 0L, name, thread, spanId, parentSpanId)

    private fun finished(label: String = "io", name: String? = null, spanId: Long? = null, parentSpanId: Long? = null) =
        TraceEvent.Finished(label, 2L, 1L, TraceOutcome.Completed, name, thread, spanId, parentSpanId)

    @Test
    fun startedAndFinishedMapToOneSlicePerRun() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)

        sink.emit(started(name = "fetch"))
        assertEquals(listOf("io:fetch"), tracer.begun.map { it.name })
        assertTrue(tracer.closed.isEmpty())

        sink.emit(finished(name = "fetch"))
        assertEquals(listOf("io:fetch"), tracer.closed)
        assertEquals("structured", tracer.begun.single().category)
    }

    @Test
    fun sliceIsNamedAfterTheLabelWhenThereIsNoCoroutineName() {
        val tracer = FakeTracer()
        PerfettoTraceSink(tracer).emit(started(label = "default"))
        assertEquals(listOf("default"), tracer.begun.map { it.name })
    }

    @Test
    fun sliceNamesAreSanitizedAndCutLikeTheAndroidSink() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)

        sink.emit(started(name = "a|b\nc\u0000d"))
        sink.emit(started(name = "x".repeat(300)))

        assertEquals("io:a b c d", tracer.begun[0].name)
        assertEquals(127, tracer.begun[1].name.length)
    }

    @Test
    fun finishedWithoutAStartedFromThisSinkClosesNothing() {
        val tracer = FakeTracer()
        PerfettoTraceSink(tracer).emit(finished())
        assertTrue(tracer.closed.isEmpty())
    }

    @Test
    fun nestedRunsOnOneThreadCloseInnermostFirst() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)

        sink.emit(started(name = "outer"))
        sink.emit(started(name = "inner"))
        sink.emit(finished(name = "inner"))
        sink.emit(finished(name = "outer"))

        assertEquals(listOf("io:inner", "io:outer"), tracer.closed)
    }

    @Test
    fun spanIdsDrawFlowsFromTheParentAndToChildren() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)

        sink.emit(started(spanId = 5L, parentSpanId = 2L))
        sink.emit(started(spanId = 2L))

        val child = tracer.begun[0].flowIds
        val root = tracer.begun[1].flowIds
        assertEquals(listOf(PerfettoTraceSink.flowId(2L), PerfettoTraceSink.flowId(5L)), child)
        assertEquals(listOf(PerfettoTraceSink.flowId(2L)), root)
    }

    @Test
    fun flowIdsLiveInTheirOwnIdSpace() {
        assertTrue(PerfettoTraceSink.flowId(1L) != 1L)
        assertTrue(PerfettoTraceSink.flowId(1L) != PerfettoTraceSink.flowId(2L))
    }

    @Test
    fun withoutASpanNoFlowTokenIsUsed() {
        val tracer = FakeTracer()
        PerfettoTraceSink(tracer).emit(started())
        assertNull(tracer.begun.single().flowIds)
    }

    @Test
    fun countersTrackPendingAndRunningPerDispatcher() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)

        sink.emit(TraceEvent.Queued("io", 0L, null))
        sink.emit(TraceEvent.Queued("io", 0L, null))
        sink.emit(started())
        sink.emit(finished())

        assertEquals(listOf(1L, 2L, 1L), tracer.counterValues("structured.io.pending"))
        assertEquals(listOf(1L, 0L), tracer.counterValues("structured.io.running"))
    }

    @Test
    fun countersAreClampedAtZeroWhenATraceStartsMidRun() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)

        sink.emit(started())
        sink.emit(finished())
        sink.emit(finished())

        assertEquals(listOf(1L, 0L), tracer.counterValues("structured.io.running"))
        assertTrue(tracer.counterValues("structured.io.pending").isEmpty())
    }

    @Test
    fun poolSamplesPublishPoolCounterTracks() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)

        sink.emit(TraceEvent.PoolSample("io", 0L, 10L, 8L, 5L, pending = 2L, running = 3, peakRunning = 4, parallelism = 64))
        sink.emit(TraceEvent.PoolSample("default", 0L, 0L, 0L, 0L, pending = 0L, running = 0, peakRunning = 0, parallelism = null))

        assertEquals(listOf(2L), tracer.counterValues("structured.io.pool.pending"))
        assertEquals(listOf(3L), tracer.counterValues("structured.io.pool.running"))
        assertEquals(listOf(4L), tracer.counterValues("structured.io.pool.peakRunning"))
        assertEquals(listOf(64L), tracer.counterValues("structured.io.pool.parallelism"))
        assertTrue(tracer.counterValues("structured.default.pool.parallelism").isEmpty())
        assertTrue(tracer.begun.isEmpty())
    }

    @Test
    fun isEnabledFollowsMaxLevelAndTheTracerCategory() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer, maxLevel = TraceLevel.Dispatch)

        assertTrue(sink.isEnabled(TraceLevel.Dispatch))
        assertFalse(sink.isEnabled(TraceLevel.Coroutine))
        assertFalse(sink.isEnabled(TraceLevel.Pool))

        tracer.enabled = false
        assertFalse(sink.isEnabled(TraceLevel.Dispatch))
    }

    @Test
    fun emitAndIsEnabledNeverThrowWhenTheTracerFails() {
        val tracer = FakeTracer(throwing = true)
        val sink = PerfettoTraceSink(tracer)

        sink.emit(TraceEvent.Queued("io", 0L, null))
        sink.emit(started())
        sink.emit(finished())
        sink.emit(TraceEvent.PoolSample("io", 0L, 0L, 0L, 0L, 0L, 0, 0, null))
        assertFalse(sink.isEnabled(TraceLevel.Dispatch))
    }

    @OptIn(DelicateTracingApi::class)
    @Test
    fun defaultsToTheGlobalTracerAndNeverSetsIt() {
        val stub = Tracer.getStubTracer()
        assertSame(stub, Tracer.global)

        val sink = PerfettoTraceSink()
        sink.emit(started())
        assertFalse(sink.isEnabled(TraceLevel.Dispatch)) // the stub tracer has every category off
        assertSame(stub, Tracer.global) // creating and using a sink never installs a global tracer

        val fake = FakeTracer()
        Tracer.setGlobalTracer(fake)
        try {
            sink.emit(started())
            assertEquals(1, fake.begun.size) // resolved lazily, so a later global is picked up
        } finally {
            Tracer.resetGlobalTracer()
        }
    }

    @Test
    fun concurrentRunsOpenAndCloseEverySlice() {
        val tracer = FakeTracer()
        val sink = PerfettoTraceSink(tracer)
        val threads = 8
        val perThread = 200
        val pool = Executors.newFixedThreadPool(threads)
        val done = CountDownLatch(threads)
        repeat(threads) {
            pool.execute {
                repeat(perThread) {
                    sink.emit(TraceEvent.Queued("io", 0L, null))
                    sink.emit(started())
                    sink.emit(finished())
                }
                done.countDown()
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()

        assertEquals(threads * perThread, tracer.begun.size)
        assertEquals(threads * perThread, tracer.closed.size)
        assertEquals(0L, tracer.counterValues("structured.io.running").last())
        assertEquals(0L, tracer.counterValues("structured.io.pending").last())
    }
}
