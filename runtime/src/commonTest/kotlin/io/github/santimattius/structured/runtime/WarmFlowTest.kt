package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private class WarmBoom(label: String, @Suppress("unused") val tag: Int) : Exception(label)

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)
class WarmFlowTest {

    @Test
    fun warmIn_isLazy_upstreamDoesNotRunUntilFirstCollector() = runTest {
        var runs = 0
        val warm = flow { runs++; emit(1) }.warmIn(backgroundScope)

        advanceUntilIdle()
        assertEquals(0, runs)

        assertEquals(listOf(1), warm.toList())
        assertEquals(1, runs)
    }

    @Test
    fun warmIn_secondCollectorReplaysWithoutRerunningUpstream() = runTest {
        var runs = 0
        val warm = flow {
            runs++
            emit(1); delay(10); emit(2); delay(10); emit(3)
        }.warmIn(backgroundScope)

        val first = warm.toList()
        val second = warm.toList()

        assertEquals(listOf(1, 2, 3), first)
        assertEquals(listOf(1, 2, 3), second)
        assertEquals(1, runs)
    }

    @Test
    fun warmIn_concurrentCollectorsShareOneExecution() = runTest {
        var runs = 0
        val warm = flow {
            runs++
            emit("a"); delay(100); emit("b")
        }.warmIn(backgroundScope)

        val a = async { warm.toList() }
        val b = async { delay(50); warm.toList() }

        assertEquals(listOf("a", "b"), a.await())
        assertEquals(listOf("a", "b"), b.await())
        assertEquals(1, runs)
    }

    @Test
    fun warmIn_upstreamFailure_isMemoizedAndRethrownToEveryCollector() = runTest {
        var runs = 0
        val warm = flow<Int> {
            runs++
            emit(1)
            throw WarmBoom("boom", 1)
        }.warmIn(backgroundScope)

        val seen1 = mutableListOf<Int>()
        val e1 = assertFailsWith<WarmBoom> { warm.collect { seen1 += it } }
        val seen2 = mutableListOf<Int>()
        val e2 = assertFailsWith<WarmBoom> { warm.collect { seen2 += it } }

        assertEquals("boom", e1.message)
        assertEquals("boom", e2.message)
        assertEquals(listOf(1), seen1)
        assertEquals(listOf(1), seen2)
        assertEquals(1, runs)
    }

    @Test
    fun warmIn_scopeCancelled_collectorsDoNotHang() = runTest {
        val scope = CoroutineScope(Job())
        val warm = flow { emit(1); delay(10_000); emit(2) }.warmIn(scope)

        val collecting = async { runCatching { warm.toList() } }
        delay(10)
        scope.coroutineContext[Job]!!.cancel()

        val result = collecting.await()
        assertEquals(true, result.isFailure)
    }
}
