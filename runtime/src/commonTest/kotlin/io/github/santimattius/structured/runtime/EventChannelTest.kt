package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class EventChannelTest {

    @Test
    fun eventSentBeforeCollecting_isDeliveredToLaterCollector() = runTest {
        val channel = EventChannel<String>()

        channel.send("hello")

        assertEquals("hello", channel.events.first())
    }

    @Test
    fun events_arriveInOrder() = runTest {
        val channel = EventChannel<Int>()
        (1..5).forEach { channel.send(it) }

        assertEquals(listOf(1, 2, 3, 4, 5), channel.events.take(5).toList())
    }

    @Test
    fun eachEvent_isDeliveredExactlyOnce() = runTest {
        val channel = EventChannel<Int>()
        channel.send(1)
        channel.send(2)
        channel.send(3)

        val first = channel.events.take(2).toList()
        // A new collection continues where the previous left off: nothing is replayed.
        val second = channel.events.take(1).toList()

        assertEquals(listOf(1, 2), first)
        assertEquals(listOf(3), second)
    }

    @Test
    fun events_areDistributedNotBroadcast_acrossCollectors() = runTest {
        val channel = EventChannel<Int>()
        val a = mutableListOf<Int>()
        val b = mutableListOf<Int>()
        val jobA = launch { channel.events.collect { a += it } }
        val jobB = launch { channel.events.collect { b += it } }

        repeat(10) { channel.send(it) }
        advanceUntilIdle()

        assertEquals((0 until 10).toList(), (a + b).sorted())
        jobA.cancel()
        jobB.cancel()
    }

    @Test
    fun trySend_returnsTrueWhileOpen_andFalseAfterClose() = runTest {
        val channel = EventChannel<Int>()

        assertTrue(channel.trySend(1))
        channel.close()

        assertFalse(channel.trySend(2))
    }

    @Test
    fun close_completesFlow_afterDeliveringBufferedEvents() = runTest {
        val channel = EventChannel<Int>()
        channel.send(1)
        channel.send(2)
        channel.close()

        assertEquals(listOf(1, 2), channel.events.toList())
    }

    @Test
    fun send_suspendsInsteadOfDropping_whenBufferIsFull() = runTest {
        val channel = EventChannel<Int>(capacity = 2)
        channel.send(1)
        channel.send(2)

        val third = async(start = CoroutineStart.UNDISPATCHED) { channel.send(3) }

        assertFalse(third.isCompleted, "send must suspend when the buffer is full")
        assertFalse(channel.trySend(4), "trySend reports rejection when the buffer is full")

        assertEquals(listOf(1, 2, 3), channel.events.take(3).toList())
        third.await()
    }
}
