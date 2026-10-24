package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class TraceModelTest {

    @Test
    fun levelsAreCumulativeByOrdinal() {
        assertTrue(TraceLevel.Dispatch < TraceLevel.Coroutine)
        assertTrue(TraceLevel.Coroutine < TraceLevel.Pool)
        assertEquals(listOf(TraceLevel.Dispatch, TraceLevel.Coroutine, TraceLevel.Pool), TraceLevel.entries)
    }

    @Test
    fun threadInfoEquality() {
        val a = ThreadInfo(1L, "main")
        assertEquals(a, ThreadInfo(1L, "main"))
        assertEquals(a.hashCode(), ThreadInfo(1L, "main").hashCode())
        assertNotEquals(a, ThreadInfo(2L, "main"))
        assertNotEquals(a, ThreadInfo(1L, "other"))
        assertEquals("ThreadInfo(id=1, name=main)", a.toString())
    }

    @Test
    fun outcomes() {
        val e = IllegalStateException("x")
        assertEquals(TraceOutcome.Threw(e), TraceOutcome.Threw(e))
        assertEquals(TraceOutcome.Threw(e).hashCode(), TraceOutcome.Threw(e).hashCode())
        assertNotEquals<TraceOutcome>(TraceOutcome.Completed, TraceOutcome.Threw(e))
        assertSame(e, TraceOutcome.Threw(e).error)
    }

    @Test
    fun queuedEqualityAndToString() {
        val q = TraceEvent.Queued("io", 10L, "job")
        assertEquals(q, TraceEvent.Queued("io", 10L, "job"))
        assertEquals(q.hashCode(), TraceEvent.Queued("io", 10L, "job").hashCode())
        assertNotEquals(q, TraceEvent.Queued("io", 11L, "job"))
        assertNotEquals(q, TraceEvent.Queued("io", 10L, null))
        assertEquals("Queued(dispatcher=io, timestampNanos=10, coroutineName=job)", q.toString())
        assertEquals(TraceLevel.Dispatch, q.level)
    }

    @Test
    fun startedEqualityAndToString() {
        val t = ThreadInfo(7L, "w-1")
        val s = TraceEvent.Started("io", 20L, 5L, "job", t)
        assertEquals(s, TraceEvent.Started("io", 20L, 5L, "job", ThreadInfo(7L, "w-1")))
        assertEquals(s.hashCode(), TraceEvent.Started("io", 20L, 5L, "job", t).hashCode())
        assertNotEquals(s, TraceEvent.Started("io", 20L, 6L, "job", t))
        assertNotEquals(s, TraceEvent.Started("io", 20L, 5L, "job", null))
        assertEquals(
            "Started(dispatcher=io, timestampNanos=20, queueDelayNanos=5, coroutineName=job, " +
                "thread=ThreadInfo(id=7, name=w-1))",
            s.toString(),
        )
        assertEquals(TraceLevel.Dispatch, s.level)
    }

    @Test
    fun finishedEqualityAndToString() {
        val f = TraceEvent.Finished("io", 30L, 9L, TraceOutcome.Completed, null, null)
        assertEquals(f, TraceEvent.Finished("io", 30L, 9L, TraceOutcome.Completed, null, null))
        assertEquals(f.hashCode(), TraceEvent.Finished("io", 30L, 9L, TraceOutcome.Completed, null, null).hashCode())
        assertNotEquals(f, TraceEvent.Finished("io", 30L, 9L, TraceOutcome.Threw(RuntimeException()), null, null))
        assertNull(f.coroutineName)
        assertNull(f.thread)
        assertEquals(
            "Finished(dispatcher=io, timestampNanos=30, runTimeNanos=9, outcome=Completed, " +
                "coroutineName=null, thread=null)",
            f.toString(),
        )
        assertEquals(TraceLevel.Dispatch, f.level)
    }

    @Test
    fun differentSubtypesAreNeverEqual() {
        assertFalse(TraceEvent.Queued("io", 1L, null).equals(TraceEvent.Started("io", 1L, 0L, null, null)))
    }

    @Test
    fun noneSinkIsDisabledAndIgnoresEvents() {
        TraceLevel.entries.forEach { assertFalse(TraceSink.None.isEnabled(it)) }
        TraceSink.None.emit(TraceEvent.Queued("io", 1L, null))
    }

    @Test
    fun defaultIsEnabledIsTrue() {
        val sink = TraceSink { }
        TraceLevel.entries.forEach { assertTrue(sink.isEnabled(it)) }
    }
}
