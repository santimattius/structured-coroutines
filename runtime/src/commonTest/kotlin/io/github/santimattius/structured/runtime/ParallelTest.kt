package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Not copyable by stack-trace recovery, so type/identity survive `withContext` boundaries. */
private class ParallelBoom(label: String, @Suppress("unused") val tag: Int) : Exception(label)

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)
class ParallelTest {

    // region parallel

    @Test
    fun parallel_preservesInputOrder() = runTest {
        val tasks = listOf<suspend () -> Int>(
            { delay(300); 1 },
            { delay(100); 2 },
            { delay(200); 3 },
        )

        assertEquals(listOf(1, 2, 3), tasks.parallel())
    }

    @Test
    fun parallel_runsConcurrently_totalTimeIsMaxNotSum() = runTest {
        val tasks = listOf<suspend () -> Unit>(
            { delay(300) },
            { delay(100) },
            { delay(200) },
        )

        tasks.parallel()

        assertEquals(300, currentTime)
    }

    @Test
    fun parallel_emptyInput_returnsEmptyList() = runTest {
        assertEquals(emptyList(), emptyList<suspend () -> Int>().parallel())
    }

    @Test
    fun parallel_failFast_propagatesAndCancelsSiblings() = runTest {
        var siblingCancelled = false
        val tasks = listOf<suspend () -> Int>(
            {
                try {
                    awaitCancellation()
                } finally {
                    siblingCancelled = true
                }
            },
            { delay(50); throw ParallelBoom("boom", 1) },
        )

        assertFailsWith<ParallelBoom> { tasks.parallel() }

        assertTrue(siblingCancelled)
        assertEquals(50, currentTime)
    }

    @Test
    fun parallel_concurrencyLimit_isHonored() = runTest {
        var running = 0
        var maxRunning = 0
        val tasks = List(6) {
            suspend {
                running++
                maxRunning = maxOf(maxRunning, running)
                delay(100)
                running--
                it
            }
        }

        val result = tasks.parallel(concurrency = 2)

        assertEquals(List(6) { it }, result)
        assertEquals(2, maxRunning)
        assertEquals(300, currentTime)
    }

    @Test
    fun parallel_nonPositiveConcurrency_throwsIllegalArgument() = runTest {
        assertFailsWith<IllegalArgumentException> { listOf<suspend () -> Int>({ 1 }).parallel(concurrency = 0) }
    }

    @Test
    fun parallel_callerCancellation_propagates() = runTest {
        val started = CompletableDeferred<Unit>()
        var completed = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            listOf<suspend () -> Unit>({ started.complete(Unit); awaitCancellation() }).parallel()
            completed = true
        }
        started.await()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertTrue(!completed)
    }

    // endregion

    // region supervisedAll

    @Test
    fun supervisedAll_isolatesFailures_keepsInputOrder() = runTest {
        val boom = ParallelBoom("boom", 1)
        val tasks = listOf<suspend () -> Int>(
            { delay(300); 1 },
            { delay(50); throw boom },
            { delay(100); 3 },
        )

        val results = tasks.supervisedAll()

        assertEquals(3, results.size)
        assertEquals(1, results[0].getOrNull())
        assertEquals(boom, results[1].exceptionOrNull())
        assertEquals(3, results[2].getOrNull())
        assertEquals(300, currentTime)
    }

    @Test
    fun supervisedAll_concurrencyLimit_isHonored() = runTest {
        var running = 0
        var maxRunning = 0
        val tasks = List(4) {
            suspend {
                running++
                maxRunning = maxOf(maxRunning, running)
                delay(100)
                running--
                it
            }
        }

        val results = tasks.supervisedAll(concurrency = 2)

        assertEquals(List(4) { it }, results.map { it.getOrThrow() })
        assertEquals(2, maxRunning)
        assertEquals(200, currentTime)
    }

    @Test
    fun supervisedAll_nonPositiveConcurrency_throwsIllegalArgument() = runTest {
        assertFailsWith<IllegalArgumentException> {
            listOf<suspend () -> Int>({ 1 }).supervisedAll(concurrency = -1)
        }
    }

    @Test
    fun supervisedAll_callerCancellation_propagatesInsteadOfCapturing() = runTest {
        val started = CompletableDeferred<Unit>()
        var results: List<Result<Unit>>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            results = listOf<suspend () -> Unit>({ started.complete(Unit); awaitCancellation() }).supervisedAll()
        }
        started.await()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(null, results)
    }

    @Test
    fun supervisedAll_taskThrowingOwnCancellation_whileCallerActive_isCapturedAsFailure() = runTest {
        val results = listOf<suspend () -> Int>(
            { throw kotlin.coroutines.cancellation.CancellationException("own") },
            { 2 },
        ).supervisedAll()

        assertIs<kotlin.coroutines.cancellation.CancellationException>(results[0].exceptionOrNull())
        assertEquals(2, results[1].getOrNull())
    }

    // endregion
}
