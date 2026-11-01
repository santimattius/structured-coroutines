@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

private fun TraceEvent.spanId(): Long? = when (this) {
    is TraceEvent.Queued -> spanId
    is TraceEvent.Started -> spanId
    is TraceEvent.Finished -> spanId
    else -> null
}

private fun TraceEvent.parentSpanId(): Long? = when (this) {
    is TraceEvent.Queued -> parentSpanId
    is TraceEvent.Started -> parentSpanId
    is TraceEvent.Finished -> parentSpanId
    else -> null
}

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class TraceSpanTest {

    private val clock = TestTimeSource()

    private fun TestScope.traced(sink: TraceSink): CoroutineDispatcher =
        TracingDispatcher(StandardTestDispatcher(testScheduler), "test", sink, clock)

    @Test
    fun spanIdsAreUniqueAndIncreasing() {
        val a = TraceSpan.root("a")
        val b = TraceSpan.root("b")
        assertNotEquals(a.id, b.id)
        assertNull(a.parentId)
        assertEquals("a", a.name)
        assertTrue(b.id > a.id)
    }

    @Test
    fun contextWithoutSpanHasNullIds() = runTest {
        val sink = RingBufferSink(64)
        withContext(traced(sink)) { delay(1) }
        val events = sink.snapshot()
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.spanId() == null && it.parentSpanId() == null })
    }

    @Test
    fun spanIdsAttachedToQueuedStartedFinished() = runTest {
        val sink = RingBufferSink(64)
        val span = TraceSpan.root("work")
        withContext(traced(sink) + span) {}
        val events = sink.snapshot()
        assertTrue(events.any { it is TraceEvent.Queued })
        assertTrue(events.any { it is TraceEvent.Started })
        assertTrue(events.any { it is TraceEvent.Finished })
        assertTrue(events.all { it.spanId() == span.id && it.parentSpanId() == null })
    }

    @Test
    fun withTraceSpanOpensChildOfCurrentSpan() = runTest {
        val root = TraceSpan.root("root")
        val inner = withContext(root) {
            withTraceSpan("child") { currentCoroutineContext()[TraceSpan]!! }
        }
        assertEquals(root.id, inner.parentId)
        assertEquals("child", inner.name)
        assertNotEquals(root.id, inner.id)
    }

    @Test
    fun withTraceSpanWithoutCurrentSpanOpensRoot() = runTest {
        val inner = withTraceSpan("solo") { currentCoroutineContext()[TraceSpan]!! }
        assertEquals("solo", inner.name)
        assertNull(inner.parentId)
    }

    @Test
    fun withTraceSpanReturnsBlockResult() = runTest {
        assertEquals(7, withTraceSpan("r") { 7 })
    }

    @Test
    fun childEventsCarryParentSpanId() = runTest {
        val sink = RingBufferSink(64)
        val dispatcher = traced(sink)
        withContext(dispatcher + TraceSpan.root("root")) {
            withTraceSpan("child") { withContext(dispatcher) { delay(1) } }
        }
        val childEvents = sink.snapshot().filter { it.parentSpanId() != null }
        assertTrue(childEvents.isNotEmpty())
        assertTrue(childEvents.all { it.spanId() != it.parentSpanId() })
    }

    @Test
    fun failingBlockStillClosesSliceWithIds() = runTest {
        val sink = RingBufferSink(64)
        val span = TraceSpan.root("boom")
        assertFailsWith<IllegalStateException> {
            withContext(traced(sink) + span) { error("boom") }
        }
        val finished = sink.snapshot().filterIsInstance<TraceEvent.Finished>()
        assertTrue(finished.isNotEmpty() && finished.all { it.spanId == span.id })
    }

    @Test
    fun cancellationStillClosesSliceWithIds() = runTest {
        val sink = RingBufferSink(64)
        val span = TraceSpan.root("cancel")
        val job = launch(traced(sink) + span) { delay(1_000) }
        testScheduler.runCurrent()
        job.cancel()
        advanceUntilIdle()
        val events = sink.snapshot()
        val started = events.filterIsInstance<TraceEvent.Started>().size
        val finished = events.filterIsInstance<TraceEvent.Finished>()
        assertEquals(started, finished.size)
        assertTrue(finished.all { it.spanId == span.id })
    }

    // --- helper integration ---

    private suspend fun spanNow(): TraceSpan? = currentCoroutineContext()[TraceSpan]

    @Test
    fun parallel_withSpan_childrenRunInChildSpan() = runTest {
        val root = TraceSpan.root("root")
        val seen = withContext(root) {
            listOf<suspend () -> TraceSpan?>({ spanNow() }, { spanNow() }).parallel()
        }
        assertTrue(seen.all { it != null && it.parentId == root.id && it.name == "parallel" })
        assertEquals(1, seen.map { it!!.id }.toSet().size)
    }

    @Test
    fun parallel_withoutSpan_noSpanInChildren() = runTest {
        val seen = listOf<suspend () -> TraceSpan?>({ spanNow() }).parallel()
        assertNull(seen.single())
    }

    @Test
    fun supervisedAll_withSpan_childrenRunInChildSpan() = runTest {
        val root = TraceSpan.root("root")
        val seen = withContext(root) {
            listOf<suspend () -> TraceSpan?>({ spanNow() }).supervisedAll()
        }
        val span = seen.single().getOrThrow()!!
        assertEquals(root.id, span.parentId)
        assertEquals("supervisedAll", span.name)
    }

    @Test
    fun supervisedAll_withoutSpan_noSpanInChildren() = runTest {
        val seen = listOf<suspend () -> TraceSpan?>({ spanNow() }).supervisedAll()
        assertNull(seen.single().getOrThrow())
    }

    @Test
    fun hedged_withSpan_attemptsRunInChildSpan() = runTest {
        val root = TraceSpan.root("root")
        val span = withContext(root) {
            hedged(10.milliseconds) { spanNow() }
        }!!
        assertEquals(root.id, span.parentId)
        assertEquals("hedged", span.name)
    }

    @Test
    fun hedged_withoutSpan_noSpanInAttempts() = runTest {
        assertNull(hedged(10.milliseconds) { spanNow() })
    }

    @Test
    fun hedged_withSpan_attemptsShareOneChildSpan() = runTest {
        val root = TraceSpan.root("root")
        val ids = withContext(root) {
            val seen = mutableListOf<Long>()
            hedged(10.milliseconds, maxAttempts = 2) { attempt ->
                seen += spanNow()!!.id
                if (attempt == 0) delay(100)
                attempt
            }
            seen
        }
        assertEquals(2, ids.size)
        assertEquals(1, ids.toSet().size)
    }

    @Test
    fun concurrentOrdered_withSpan_tasksRunInChildSpan() = runTest {
        val root = TraceSpan.root("root")
        val seen = withContext(root) {
            listOf<suspend () -> TraceSpan?>({ spanNow() }).concurrentOrdered().toList()
        }
        val span = seen.single()!!
        assertEquals(root.id, span.parentId)
        assertEquals("concurrentOrdered", span.name)
    }

    @Test
    fun concurrentOrdered_withoutSpan_noSpanInTasks() = runTest {
        val seen = listOf<suspend () -> TraceSpan?>({ spanNow() }).concurrentOrdered().toList()
        assertNull(seen.single())
    }

    @Test
    fun parallel_withSpan_eventsLinkToParentSpan() = runTest {
        val sink = RingBufferSink(256)
        val dispatcher = traced(sink)
        val root = TraceSpan.root("root")
        withContext(dispatcher + root) {
            listOf<suspend () -> Unit>({ delay(1) }, { delay(1) }).parallel()
        }
        val events = sink.snapshot()
        assertTrue(events.any { it.spanId() == root.id })
        assertTrue(events.any { it.parentSpanId() == root.id })
    }
}
