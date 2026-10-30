@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RecordingSinkTest {

    private fun queued(ts: Long) = TraceEvent.Queued("io", ts, coroutineName = null)
    private fun started(ts: Long) =
        TraceEvent.Started("io", ts, queueDelayNanos = 1, coroutineName = null, thread = null)
    private fun finished(ts: Long) =
        TraceEvent.Finished("io", ts, runTimeNanos = 1, outcome = TraceOutcome.Completed, coroutineName = null, thread = null)

    @Test
    fun startsEmpty() {
        assertTrue(RecordingSink().events.isEmpty())
    }

    @Test
    fun recordsEveryEmittedEventInOrder() {
        val sink = RecordingSink()
        val a = queued(1)
        val b = started(2)
        val c = finished(3)

        sink.emit(a)
        sink.emit(b)
        sink.emit(c)

        assertEquals(listOf<TraceEvent>(a, b, c), sink.events)
    }

    @Test
    fun eventsOfFiltersByType() {
        val sink = RecordingSink()
        val q = queued(1)
        val f = finished(3)
        sink.emit(q)
        sink.emit(started(2))
        sink.emit(f)

        assertEquals(listOf(q), sink.eventsOf<TraceEvent.Queued>())
        assertEquals(listOf(f), sink.eventsOf<TraceEvent.Finished>())
        assertTrue(sink.eventsOf<TraceEvent.PoolSample>().isEmpty())
    }

    @Test
    fun clearDropsRecordedEvents() {
        val sink = RecordingSink()
        sink.emit(queued(1))

        sink.clear()
        assertTrue(sink.events.isEmpty())

        val after = queued(2)
        sink.emit(after)
        assertEquals(listOf<TraceEvent>(after), sink.events)
    }

    @Test
    fun eventsIsASnapshotUnaffectedByLaterEmits() {
        val sink = RecordingSink()
        sink.emit(queued(1))
        val snapshot = sink.events

        sink.emit(queued(2))

        assertEquals(1, snapshot.size)
        assertEquals(2, sink.events.size)
    }

    @Test
    fun everyLevelIsEnabledByDefault() {
        val sink = RecordingSink()
        TraceLevel.entries.forEach { assertTrue(sink.isEnabled(it), "$it") }
    }

    @Test
    fun maxLevelEnablesLevelsUpToItsOrdinal() {
        val sink = RecordingSink(maxLevel = TraceLevel.Coroutine)

        assertTrue(sink.isEnabled(TraceLevel.Dispatch))
        assertTrue(sink.isEnabled(TraceLevel.Coroutine))
        assertFalse(sink.isEnabled(TraceLevel.Pool))
    }

    @Test
    fun nullMaxLevelDisablesEveryLevel() {
        val sink = RecordingSink(maxLevel = null)
        TraceLevel.entries.forEach { assertFalse(sink.isEnabled(it), "$it") }
    }

    @Test
    fun recordsEventsEvenWhenTheirLevelIsDisabled() {
        // Filtering by isEnabled is the caller's job (as in CompositeSink); the recorder keeps
        // whatever it is handed so tests can assert that a caller did or did not emit.
        val sink = RecordingSink(maxLevel = null)
        val event = queued(1)

        sink.emit(event)

        assertSame(event, sink.events.single())
    }

    @Test
    fun recordsAllEventsEmittedConcurrently() = runTest {
        val sink = RecordingSink()
        val coroutines = 50
        val perCoroutine = 100

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(coroutines) { c ->
                    launch { repeat(perCoroutine) { i -> sink.emit(queued((c * perCoroutine + i).toLong())) } }
                }
            }
        }

        assertEquals(coroutines * perCoroutine, sink.events.size)
    }
}
