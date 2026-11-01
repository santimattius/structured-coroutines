package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/** Not copyable by stack-trace recovery, so type/identity survive `withContext` boundaries. */
private class TimeoutsBoom(label: String, @Suppress("unused") val tag: Int) : Exception(label)

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class)
class TimeoutsTest {

    @Test
    fun withTimeoutResult_timeoutFires_returnsFailureWithTimeoutException() = runTest {
        val result = withTimeoutResult<Int>(100) {
            delay(1_000)
            1
        }

        assertIs<TimeoutCancellationException>(result.exceptionOrNull())
        assertEquals(100, currentTime)
    }

    @Test
    fun withTimeoutResult_completesInTime_returnsSuccess() = runTest {
        val result = withTimeoutResult(1_000) {
            delay(10)
            "ok"
        }

        assertEquals("ok", result.getOrNull())
    }

    @Test
    fun withTimeoutResult_blockException_propagatesNotCaptured() = runTest {
        assertFailsWith<TimeoutsBoom> {
            withTimeoutResult<Int>(1_000) { throw TimeoutsBoom("boom", 1) }
        }
    }

    @Test
    fun withTimeoutResult_outerCancellation_propagates() = runTest {
        val started = CompletableDeferred<Unit>()
        var captured: Result<Int>? = null
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            captured = withTimeoutResult(10_000) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(null, captured)
    }

    @Test
    fun withTimeoutResult_outerTimeoutFiring_propagatesInsteadOfBecomingFailure() = runTest {
        var captured: Result<Int>? = null

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(100) {
                captured = withTimeoutResult(10_000) {
                    delay(5_000)
                    1
                }
            }
        }

        assertEquals(null, captured)
    }

    @Test
    fun withTimeoutResult_nestedTimeoutEscapingBlock_propagates() = runTest {
        assertFailsWith<TimeoutCancellationException> {
            withTimeoutResult<Int>(10_000) {
                withTimeout(50) { delay(1_000) }
                1
            }
        }
    }

    @Test
    fun withTimeoutResult_durationOverload_timeoutFiresAndSuccess() = runTest {
        val failure = withTimeoutResult<Int>(100.milliseconds) {
            delay(1_000)
            1
        }
        val success = withTimeoutResult(100.milliseconds) { 7 }

        assertIs<TimeoutCancellationException>(failure.exceptionOrNull())
        assertEquals(7, success.getOrNull())
    }

    @Test
    fun withTimeoutResult_isNotCancellationRethrowForCallerWhenOwnTimeout() = runTest {
        // A caller that is still active keeps running after the failure value is returned.
        val result = withTimeoutResult<Unit>(10) { awaitCancellation() }

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is CancellationException)
    }
}
