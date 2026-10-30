package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class RingBufferSinkTest {

    private fun ev(i: Int) = TraceEvent.Queued("io", i.toLong(), null)

    @Test
    fun keepsEventsInOrderBelowCapacity() {
        val sink = RingBufferSink(5)
        (1..3).forEach { sink.emit(ev(it)) }
        assertEquals((1..3).map(::ev), sink.snapshot())
    }

    @Test
    fun keepsOnlyLastNAndWraps() {
        val sink = RingBufferSink(3)
        (1..10).forEach { sink.emit(ev(it)) }
        assertEquals((8..10).map(::ev), sink.snapshot())
    }

    @Test
    fun exactCapacity() {
        val sink = RingBufferSink(4)
        (1..4).forEach { sink.emit(ev(it)) }
        assertEquals((1..4).map(::ev), sink.snapshot())
    }

    @Test
    fun clearEmptiesAndStillWorks() {
        val sink = RingBufferSink(3)
        (1..5).forEach { sink.emit(ev(it)) }
        sink.clear()
        assertTrue(sink.snapshot().isEmpty())
        sink.emit(ev(6))
        assertEquals(listOf(ev(6)), sink.snapshot())
    }

    @Test
    fun rejectsNonPositiveCapacity() {
        assertFailsWith<IllegalArgumentException> { RingBufferSink(0) }
        assertFailsWith<IllegalArgumentException> { RingBufferSink(-1) }
    }

    @Test
    fun enabledUpToMaxLevel() {
        val default = RingBufferSink(1)
        TraceLevel.entries.forEach { assertTrue(default.isEnabled(it)) }
        val capped = RingBufferSink(1, TraceLevel.Coroutine)
        assertTrue(capped.isEnabled(TraceLevel.Dispatch))
        assertTrue(capped.isEnabled(TraceLevel.Coroutine))
        assertFalse(capped.isEnabled(TraceLevel.Pool))
    }

    @Test
    fun snapshotIsADetachedCopy() {
        val sink = RingBufferSink(2)
        sink.emit(ev(1))
        val snap = sink.snapshot()
        sink.emit(ev(2))
        assertEquals(1, snap.size)
    }
}
