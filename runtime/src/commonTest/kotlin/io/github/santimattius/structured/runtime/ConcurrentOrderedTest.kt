package io.github.santimattius.structured.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class OrderedBoom(label: String, @Suppress("unused") val tag: Int) : Exception(label)

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)
class ConcurrentOrderedTest {

    @Test
    fun concurrentOrdered_emitsInInputOrderEvenWhenLaterTasksFinishFirst() = runTest {
        val tasks = listOf<suspend () -> String>(
            { delay(300); "a" },
            { delay(100); "b" },
            { delay(200); "c" },
        )

        assertEquals(listOf("a", "b", "c"), tasks.concurrentOrdered().toList())
    }

    @Test
    fun concurrentOrdered_runsTasksConcurrently_totalTimeIsTheSlowest() = runTest {
        val tasks = List(3) { i -> suspend { delay(1_000); i } }

        tasks.concurrentOrdered().toList()

        assertEquals(1_000, currentTime)
    }

    @Test
    fun concurrentOrdered_emitsEachResultAsSoonAsItsTurnIsReady() = runTest {
        val tasks = listOf<suspend () -> String>(
            { delay(100); "a" },
            { delay(500); "b" },
        )
        val emittedAt = mutableListOf<Long>()

        tasks.concurrentOrdered().onEach { emittedAt += currentTime }.toList()

        assertEquals(listOf(100L, 500L), emittedAt)
    }

    @Test
    fun concurrentOrdered_isCold_nothingStartsUntilCollected() = runTest {
        var started = 0
        val flow = listOf<suspend () -> Int>({ started++; 1 }).concurrentOrdered()

        assertEquals(0, started)
        flow.toList()
        flow.toList()
        assertEquals(2, started)
    }

    @Test
    fun concurrentOrdered_taskFailure_failsFastAndCancelsTheRest() = runTest {
        var slowCancelled = false
        val tasks = listOf<suspend () -> Int>(
            {
                try { delay(10_000); 1 } finally { slowCancelled = true }
            },
            { delay(50); throw OrderedBoom("boom", 1) },
        )

        val e = assertFailsWith<OrderedBoom> { tasks.concurrentOrdered().toList() }

        assertEquals("boom", e.message)
        assertEquals(50, currentTime)
        assertEquals(true, slowCancelled)
    }

    @Test
    fun concurrentOrdered_earlyCollectorCompletion_cancelsRemainingTasks() = runTest {
        var cancelled = false
        val tasks = listOf<suspend () -> Int>(
            { 1 },
            { try { delay(10_000); 2 } finally { cancelled = true } },
        )

        assertEquals(1, tasks.concurrentOrdered().first())
        assertEquals(true, cancelled)
    }

    @Test
    fun concurrentOrdered_concurrencyLimit_neverRunsMoreTasksAtOnceAndKeepsInputOrder() = runTest {
        var running = 0
        var peak = 0
        val tasks = List(5) { i ->
            suspend {
                running++
                peak = maxOf(peak, running)
                delay(100)
                running--
                i
            }
        }

        val result = tasks.concurrentOrdered(concurrency = 2).toList()

        assertEquals(listOf(0, 1, 2, 3, 4), result)
        assertEquals(2, peak)
        assertEquals(300, currentTime) // ceil(5 / 2) waves of 100ms
    }

    @Test
    fun concurrentOrdered_invalidConcurrency_throwsWhenCalled() = runTest {
        val tasks = listOf<suspend () -> Int>({ 1 })

        assertFailsWith<IllegalArgumentException> { tasks.concurrentOrdered(concurrency = 0) }
    }
}
