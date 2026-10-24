package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Not copyable by stack-trace recovery, so identity survives coroutine boundaries. */
private class HedgedBoom(label: String, @Suppress("unused") val tag: Int) : Exception(label)

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)
class HedgedTest {

    @Test
    fun hedged_fastPrimary_winsAndNoSecondAttemptStarts() = runTest {
        val started = mutableListOf<Int>()

        val result = hedged(100.milliseconds, maxAttempts = 3) { attempt ->
            started += attempt
            delay(50)
            "primary"
        }

        assertEquals("primary", result)
        assertEquals(listOf(0), started)
        assertEquals(50, currentTime)
    }

    @Test
    fun hedged_slowPrimary_hedgeWinsAndPrimaryIsCancelled() = runTest {
        var primaryCancelled = false

        val result = hedged(100.milliseconds, maxAttempts = 2) { attempt ->
            if (attempt == 0) {
                try {
                    delay(10_000)
                    "primary"
                } catch (e: CancellationException) {
                    primaryCancelled = true
                    throw e
                }
            } else {
                delay(20)
                "hedge"
            }
        }

        assertEquals("hedge", result)
        assertEquals(120, currentTime)
        assertTrue(primaryCancelled)
    }

    @Test
    fun hedged_earlyFailure_startsNextAttemptImmediately() = runTest {
        val result = hedged(1_000.milliseconds, maxAttempts = 2) { attempt ->
            if (attempt == 0) {
                delay(10)
                throw HedgedBoom("primary", 0)
            }
            "hedge"
        }

        assertEquals("hedge", result)
        assertEquals(10, currentTime)
    }

    @Test
    fun hedged_failureOfOneAttemptWhileAnotherRuns_doesNotLaunchEarly() = runTest {
        val started = mutableListOf<Pair<Int, Long>>()

        val result = hedged(100.milliseconds, maxAttempts = 3) { attempt ->
            started += attempt to currentTime
            when (attempt) {
                0 -> { delay(110); throw HedgedBoom("primary", 0) }
                1 -> { delay(500); "hedge1" }
                else -> { delay(10_000); "hedge2" }
            }
        }

        assertEquals("hedge1", result)
        // attempt 2 would only start at 200 (100 after attempt 1); primary failing at 110 must not trigger it.
        assertEquals(listOf(0 to 0L, 1 to 100L, 2 to 200L), started)
    }

    @Test
    fun hedged_allAttemptsFail_rethrowsFirstFailureWithOthersSuppressed() = runTest {
        val e = assertFailsWith<HedgedBoom> {
            hedged<Int>(100.milliseconds, maxAttempts = 3) { attempt ->
                delay(10)
                throw HedgedBoom("attempt-$attempt", attempt)
            }
        }

        assertEquals("attempt-0", e.message)
        assertEquals(listOf("attempt-1", "attempt-2"), e.suppressedExceptions.map { it.message })
    }

    @Test
    fun hedged_maxAttemptsRespected() = runTest {
        var started = 0

        hedged(10.milliseconds, maxAttempts = 3) { attempt ->
            started++
            if (attempt < 2) awaitCancellation() else {
                delay(1_000)
                "late"
            }
        }

        assertEquals(3, started)
    }

    @Test
    fun hedged_singleAttempt_neverHedges() = runTest {
        var started = 0

        val result = hedged(10.milliseconds, maxAttempts = 1) {
            started++
            delay(500)
            "only"
        }

        assertEquals("only", result)
        assertEquals(1, started)
    }

    @Test
    fun hedged_callerCancellation_cancelsAllAttempts() = runTest {
        var cancelled = 0
        val bothStarted = CompletableDeferred<Unit>()
        var started = 0

        val job = launch {
            hedged<Unit>(10.milliseconds, maxAttempts = 2) {
                started++
                if (started == 2) bothStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled++
                }
            }
        }
        bothStarted.await()
        job.cancel()
        job.join()

        assertEquals(2, cancelled)
        assertTrue(job.isCancelled)
    }

    @Test
    fun hedged_doesNotLeakChildren() = runTest {
        hedged(10.milliseconds, maxAttempts = 3) { attempt ->
            if (attempt == 0) awaitCancellation() else {
                delay(1)
                "done"
            }
        }

        coroutineContext.job.children.forEach { it.join() }
        assertTrue(coroutineContext.job.children.none { it.isActive })
    }

    @Test
    fun hedged_invalidArguments_throw() = runTest {
        assertFailsWith<IllegalArgumentException> { hedged(10.milliseconds, maxAttempts = 0) { 1 } }
        assertFailsWith<IllegalArgumentException> { hedged((-1).milliseconds) { 1 } }
    }

    @Test
    fun hedged_attemptThrowsOwnCancellation_isTreatedAsFailure() = runTest {
        val result = hedged(1_000.milliseconds, maxAttempts = 2) { attempt ->
            if (attempt == 0) throw CancellationException("self")
            "hedge"
        }
        assertEquals("hedge", result)
        assertSame("hedge", result)
    }
}
