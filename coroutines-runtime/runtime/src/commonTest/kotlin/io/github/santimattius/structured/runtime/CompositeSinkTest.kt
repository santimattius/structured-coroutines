package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class CompositeSinkTest {

    private class Recording(private val max: TraceLevel? = TraceLevel.Pool) : TraceSink {
        val events = mutableListOf<TraceEvent>()
        override fun emit(event: TraceEvent) { events += event }
        override fun isEnabled(level: TraceLevel) = max != null && level <= max
    }

    private class FakeVmError : Error("x")

    private val queued = TraceEvent.Queued("io", 1L, null)

    @Test
    fun fansOutToAllSinks() {
        val a = Recording(); val b = Recording()
        CompositeSink(a, b).emit(queued)
        assertEquals(listOf<TraceEvent>(queued), a.events)
        assertEquals(listOf<TraceEvent>(queued), b.events)
    }

    @Test
    fun throwingSinkDoesNotStopOthers() {
        val a = Recording(); val b = Recording()
        val bad = object : TraceSink { override fun emit(event: TraceEvent) = throw IllegalStateException("boom") }
        CompositeSink(a, bad, b).emit(queued)
        assertEquals(1, a.events.size)
        assertEquals(1, b.events.size)
    }

    @Test
    fun vmErrorsPropagate() {
        val bad = object : TraceSink { override fun emit(event: TraceEvent) = throw FakeVmError() }
        assertFailsWith<FakeVmError> { CompositeSink(bad).emit(queued) }
    }

    @Test
    fun isEnabledIsAnyAggregation() {
        val composite = CompositeSink(Recording(TraceLevel.Dispatch), Recording(TraceLevel.Coroutine))
        assertTrue(composite.isEnabled(TraceLevel.Dispatch))
        assertTrue(composite.isEnabled(TraceLevel.Coroutine))
        assertFalse(composite.isEnabled(TraceLevel.Pool))
        assertFalse(CompositeSink(emptyList()).isEnabled(TraceLevel.Dispatch))
    }

    @Test
    fun onlyEnabledSinksReceiveEvent() {
        val off = Recording(null)
        val on = Recording()
        CompositeSink(off, on).emit(queued)
        assertTrue(off.events.isEmpty())
        assertEquals(1, on.events.size)
    }

    @Test
    fun throwingIsEnabledIsIsolatedToo() {
        val on = Recording()
        val bad = object : TraceSink {
            override fun emit(event: TraceEvent) {}
            override fun isEnabled(level: TraceLevel): Boolean = throw IllegalStateException("x")
        }
        CompositeSink(bad, on).emit(queued)
        assertEquals(1, on.events.size)
    }
}
