package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

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

    private fun TestScope.warmScope() = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())

    @Test
    fun warmIn_scopeCancelled_collectorsDoNotHang() = runTest {
        val scope = warmScope()
        val warm = flow { emit(1); delay(10_000); emit(2) }.warmIn(scope)

        val collecting = async { runCatching { warm.toList() } }
        advanceTimeBy(10)
        scope.cancel()
        advanceUntilIdle()

        assertTrue(collecting.isCompleted, "collector hung after scope cancellation")
        assertTrue(collecting.await().isFailure)
    }

    @Test
    fun warmIn_scopeCancelledBeforeFirstCollector_collectorFailsWithoutHanging() = runTest {
        val scope = warmScope()
        val warm = flow { emit(1) }.warmIn(scope)
        scope.cancel()

        val collecting = async { runCatching { warm.toList() } }
        advanceUntilIdle()

        assertTrue(collecting.isCompleted, "collector hung on an already-cancelled scope")
        assertTrue(collecting.await().isFailure)
    }

    @Test
    fun warmIn_scopeCancelledAfterStartBeforeBodyRuns_collectorFailsWithoutHanging() = runTest {
        val scope = warmScope()
        var ran = false
        val warm = flow { ran = true; emit(1) }.warmIn(scope)

        // UNDISPATCHED: the collector calls job.start() (body queued on the test dispatcher)
        // and suspends, all before the scheduler gets to run the body.
        val collecting = async(start = CoroutineStart.UNDISPATCHED) { runCatching { warm.toList() } }
        scope.cancel()
        advanceUntilIdle()

        assertFalse(ran, "precondition: body must not have run")
        assertTrue(collecting.isCompleted, "collector hung when cancelled before the body ran")
        assertTrue(collecting.await().isFailure)
    }

    @Test
    fun warmIn_scopeCancelledMidUpstream_replaysValuesThenFailsForEveryCollector() = runTest {
        val scope = warmScope()
        val warm = flow { emit(1); delay(10_000); emit(2) }.warmIn(scope)

        val seen1 = mutableListOf<Int>()
        val first = async { runCatching { warm.collect { seen1 += it } } }
        advanceTimeBy(10)
        assertEquals(listOf(1), seen1)
        scope.cancel()
        advanceUntilIdle()

        assertTrue(first.isCompleted, "first collector hung")
        assertTrue(first.await().isFailure)
        assertEquals(listOf(1), seen1)

        val seen2 = mutableListOf<Int>()
        val second = async { runCatching { warm.collect { seen2 += it } } }
        advanceUntilIdle()

        assertTrue(second.isCompleted, "late collector hung")
        assertTrue(second.await().isFailure)
        assertEquals(listOf(1), seen2)
    }

    @Test
    fun warmIn_normalCompletion_marksDoneOnceAndLaterCollectorsReplayExactly() = runTest {
        val scope = warmScope()
        var runs = 0
        val warm = flow { runs++; emit(1); emit(2) }.warmIn(scope)

        val first = async { warm.toList() }
        advanceUntilIdle()
        assertEquals(listOf(1, 2), first.await())

        scope.cancel() // completion after normal finish must not turn it into a failure
        advanceUntilIdle()

        assertEquals(listOf(1, 2), warm.toList())
        assertEquals(1, runs)
    }

    @Test
    fun warmIn_upstreamFailure_isNotOverwrittenByCompletionHandler() = runTest {
        val scope = warmScope()
        val boom = WarmBoom("boom", 7)
        val warm = flow<Int> { emit(1); throw boom }.warmIn(scope)

        val first = async { runCatching { warm.toList() } }
        advanceUntilIdle()
        scope.cancel()
        advanceUntilIdle()

        assertSame(boom, first.await().exceptionOrNull())
        assertSame(boom, runCatching { warm.toList() }.exceptionOrNull())
    }

    @Test
    fun warmIn_scopeCancelled_collectorSeesAFailureThatIsNotACancellation() = runTest {
        val scope = warmScope()
        val warm = flow { emit(1); delay(10_000); emit(2) }.warmIn(scope)

        val collecting = async { runCatching { warm.toList() } }
        advanceTimeBy(10)
        scope.cancel()
        advanceUntilIdle()

        val error = collecting.await().exceptionOrNull()
        // A CancellationException escaping a collector that was not cancelled would end its
        // coroutine silently instead of surfacing the failure.
        assertFalse(error is CancellationException, "got $error")
        assertTrue(error?.cause is CancellationException, "the original cancellation is kept as cause")
    }

    @Test
    fun warmIn_upstreamThrowsCancellation_collectorSeesAFailureThatIsNotACancellation() = runTest {
        val scope = warmScope()
        val own = CancellationException("upstream gave up")
        val warm = flow<Int> { throw own }.warmIn(scope)

        val error = async { runCatching { warm.toList() } }.also { advanceUntilIdle() }.await().exceptionOrNull()

        assertFalse(error is CancellationException, "got $error")
        assertSame(own, error?.cause)
    }
}
