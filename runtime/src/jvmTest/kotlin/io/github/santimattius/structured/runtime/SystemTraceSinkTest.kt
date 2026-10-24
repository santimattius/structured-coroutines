@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

private class FakeSystemTrace(@Volatile var enabled: Boolean = true) : SystemTraceApi {
    val calls = CopyOnWriteArrayList<String>()
    val perThread = java.util.concurrent.ConcurrentHashMap<Long, MutableList<String>>()
    var throwing = false

    private fun record(call: String) {
        if (throwing) throw IllegalStateException("boom")
        calls += call
        perThread.getOrPut(Thread.currentThread().id) { CopyOnWriteArrayList() } += call
    }

    override fun isEnabled(): Boolean {
        if (throwing) throw IllegalStateException("boom")
        return enabled
    }
    override fun beginSection(name: String) = record("begin:$name")
    override fun endSection() = record("end")
    override fun setCounter(name: String, value: Long) = record("counter:$name=$value")

    fun sections(): List<String> = calls.filter { !it.startsWith("counter:") }
    fun counters(): List<String> = calls.filter { it.startsWith("counter:") }.map { it.removePrefix("counter:") }
}

private fun queued(label: String = "io", name: String? = null) = TraceEvent.Queued(label, 0L, name)
private fun started(label: String = "io", name: String? = null) =
    TraceEvent.Started(label, 1L, 1L, name, null)
private fun finished(label: String = "io", name: String? = null) =
    TraceEvent.Finished(label, 2L, 1L, TraceOutcome.Completed, name, null)

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)
class SystemTraceSinkTest {

    @Test
    fun poolSampleIsIgnoredWithoutTouchingSectionsOrCounters() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api, TraceLevel.Pool)
        sink.emit(TraceEvent.PoolSample("io", 0L, 5L, 4L, 3L, 1L, 1, 2, 4))
        assertEquals(emptyList(), api.calls.toList())
    }

    @Test
    fun startedAndFinishedProduceBalancedSection() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("io"))
        sink.emit(finished("io"))
        assertEquals(listOf("begin:io", "end"), api.sections())
    }

    @Test
    fun sliceNameUsesCoroutineNameWhenPresent() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("io", "fetch"))
        sink.emit(finished("io", "fetch"))
        assertEquals(listOf("begin:io:fetch", "end"), api.sections())
    }

    @Test
    fun sanitizesSeparatorsAndNul() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("io", "a|b\nc\u0000d"))
        assertEquals(listOf("begin:io:a b c d"), api.sections())
    }

    @Test
    fun truncatesTo127CodeUnits() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("io", "x".repeat(500)))
        val name = api.sections().single().removePrefix("begin:")
        assertEquals(127, name.length)
    }

    @Test
    fun doesNotSplitSurrogatePairAtTheCut() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        // "io:" is 3 units; 123 'x' -> 126 units, then an emoji (2 units) would end at 128.
        val coroutineName = "x".repeat(123) + "😀" + "tail"
        sink.emit(started("io", coroutineName))
        val name = api.sections().single().removePrefix("begin:")
        assertTrue(name.length <= 127)
        assertFalse(name.last().isHighSurrogate(), "must not end with a lone high surrogate")
        assertEquals(126, name.length)
    }

    @Test
    fun keepsSurrogatePairWhenItFits() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("io", "x".repeat(122) + "😀" + "tail"))
        val name = api.sections().single().removePrefix("begin:")
        assertEquals(127, name.length)
        assertTrue(name.last().isLowSurrogate())
    }

    @Test
    fun finishedWithoutStartedOnThisThreadDoesNotEndSection() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(finished("io"))
        assertEquals(emptyList(), api.sections())
        // A later balanced pair still works and does not over-close.
        sink.emit(started("io"))
        sink.emit(finished("io"))
        sink.emit(finished("io"))
        assertEquals(listOf("begin:io", "end"), api.sections())
    }

    @Test
    fun finishedOnAnotherThreadDoesNotEndSection() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("io"))
        val t = Thread { sink.emit(finished("io")) }
        t.start(); t.join()
        assertEquals(listOf("begin:io"), api.sections())
    }

    @Test
    fun nestedSectionsOnOneThreadStayBalanced() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("a"))
        sink.emit(started("b"))
        sink.emit(finished("b"))
        sink.emit(finished("a"))
        assertEquals(listOf("begin:a", "begin:b", "end", "end"), api.sections())
    }

    @Test
    fun sectionsOnSeveralThreadsStayBalancedPerThread() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val done = CountDownLatch(threads)
        repeat(threads) { i ->
            pool.execute {
                ready.countDown(); ready.await()
                repeat(200) {
                    sink.emit(started("t$i"))
                    sink.emit(finished("t$i"))
                }
                done.countDown()
            }
        }
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()
        assertEquals(threads, api.perThread.size)
        for ((_, list) in api.perThread) {
            val sections = list.filter { !it.startsWith("counter:") }
            assertEquals(400, sections.size)
            sections.chunked(2).forEach { (b, e) ->
                assertTrue(b.startsWith("begin:"), b)
                assertEquals("end", e)
            }
        }
    }

    @Test
    fun countersFollowQueuedStartedFinished() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(queued("io"))
        sink.emit(queued("io"))
        sink.emit(started("io"))
        sink.emit(finished("io"))
        assertEquals(
            listOf(
                "structured.io.pending=1",
                "structured.io.pending=2",
                "structured.io.pending=1",
                "structured.io.running=1",
                "structured.io.running=0",
            ),
            api.counters(),
        )
    }

    @Test
    fun countersClampAtZeroAndDoNotRepublishUnchangedValue() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(started("io")) // Started without Queued: pending stays 0
        sink.emit(finished("io"))
        sink.emit(finished("io")) // running already 0
        assertEquals(listOf("structured.io.running=1", "structured.io.running=0"), api.counters())
    }

    @Test
    fun countersAreKeptPerLabel() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        sink.emit(queued("io"))
        sink.emit(queued("default"))
        assertEquals(listOf("structured.io.pending=1", "structured.default.pending=1"), api.counters())
    }

    @Test
    fun isEnabledHonorsMaxLevelAndApi() {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api, TraceLevel.Dispatch)
        assertTrue(sink.isEnabled(TraceLevel.Dispatch))
        assertFalse(sink.isEnabled(TraceLevel.Coroutine))
        api.enabled = false
        assertFalse(sink.isEnabled(TraceLevel.Dispatch))
        assertTrue(SystemTraceSink(FakeSystemTrace()).isEnabled(TraceLevel.Coroutine))
        assertFalse(SystemTraceSink(FakeSystemTrace()).isEnabled(TraceLevel.Pool))
    }

    @Test
    fun throwingApiNeverPropagates() {
        val api = FakeSystemTrace().apply { throwing = true }
        val sink = SystemTraceSink(api)
        sink.emit(queued())
        sink.emit(started())
        sink.emit(finished())
        assertFalse(sink.isEnabled(TraceLevel.Dispatch))
    }

    @Test
    fun endToEndOverTracingDispatcher() = runTest {
        val api = FakeSystemTrace()
        val sink = SystemTraceSink(api)
        val d = TracingDispatcher(StandardTestDispatcher(testScheduler), "io", sink)
        launch(d + CoroutineName("job")) { }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("begin:io:job", "end"), api.sections())
        assertEquals(
            listOf(
                "structured.io.pending=1",
                "structured.io.pending=0",
                "structured.io.running=1",
                "structured.io.running=0",
            ),
            api.counters(),
        )
    }

    @Test
    fun disabledApiForwardsSameRunnable() {
        val api = FakeSystemTrace(enabled = false)
        val sink = SystemTraceSink(api)
        var seen: kotlinx.coroutines.Runnable? = null
        val recording = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: kotlinx.coroutines.Runnable) {
                seen = block
            }
        }
        val d = TracingDispatcher(recording, "io", sink)
        val original = kotlinx.coroutines.Runnable { }
        d.dispatch(kotlin.coroutines.EmptyCoroutineContext, original)
        assertSame(original, seen)
        assertEquals(emptyList(), api.calls.toList())
    }
}
