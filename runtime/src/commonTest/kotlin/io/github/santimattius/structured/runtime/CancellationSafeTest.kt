package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Not copyable by coroutines' stack-trace recovery (no standard constructor), so identity
 * and suppressed exceptions survive the `withContext` boundary and can be asserted exactly.
 */
private class UncopyableException(
    label: String,
    @Suppress("unused") val tag: Int,
) : Exception(label)

@OptIn(ExperimentalStructuredCoroutines::class)
class CancellationSafeTest {
    // region runCatchingCancellable

    @Test
    fun runCatchingCancellable_success_returnsValue() {
        val result = runCatchingCancellable { 42 }

        assertEquals(42, result.getOrNull())
    }

    @Test
    fun runCatchingCancellable_ordinaryException_isCapturedInResult() {
        val boom = IllegalStateException("boom")

        val result = runCatchingCancellable<Int> { throw boom }

        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun runCatchingCancellable_cancellationException_isRethrownNotCaptured() {
        val cancellation = CancellationException("cancelled")

        val thrown =
            assertFailsWith<CancellationException> {
                runCatchingCancellable<Int> { throw cancellation }
            }

        assertSame(cancellation, thrown)
    }

    @Test
    fun runCatchingCancellable_extension_success_usesReceiver() {
        val result = "abc".runCatchingCancellable { length }

        assertEquals(3, result.getOrNull())
    }

    @Test
    fun runCatchingCancellable_extension_ordinaryException_isCaptured() {
        val boom = IllegalArgumentException("boom")

        val result = "abc".runCatchingCancellable<String, Int> { throw boom }

        assertSame(boom, result.exceptionOrNull())
    }

    @Test
    fun runCatchingCancellable_extension_cancellationException_isRethrown() {
        assertFailsWith<CancellationException> {
            "abc".runCatchingCancellable<String, Int> { throw CancellationException("cancelled") }
        }
    }

    // endregion

    // region withCleanup

    @Test
    fun withCleanup_success_returnsValueAndRunsCleanupAfterBlock() =
        runTest {
            val events = mutableListOf<String>()

            val result =
                withCleanup(
                    cleanup = { events += "cleanup" },
                    block = {
                        events += "block"
                        7
                    },
                )

            assertEquals(7, result)
            assertEquals(listOf("block", "cleanup"), events)
        }

    @Test
    fun withCleanup_blockThrows_cleanupRunsAndExceptionPropagates() =
        runTest {
            var cleaned = false
            val boom = IllegalStateException("boom")

            val thrown =
                assertFailsWith<IllegalStateException> {
                    withCleanup(cleanup = { cleaned = true }) { throw boom }
                }

            assertSame(boom, thrown)
            assertTrue(cleaned)
        }

    @Test
    fun withCleanup_cancelled_cleanupRunsAndCanSuspend() =
        runTest {
            val started = CompletableDeferred<Unit>()
            var cleanupCompleted = false

            val job =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    withCleanup(
                        cleanup = {
                            delay(100) // would throw immediately if not under NonCancellable
                            cleanupCompleted = true
                        },
                        block = {
                            started.complete(Unit)
                            delay(Long.MAX_VALUE)
                        },
                    )
                }
            started.await()

            job.cancelAndJoin()

            assertTrue(job.isCancelled)
            assertTrue(cleanupCompleted)
        }

    @Test
    fun withCleanup_blockAndCleanupThrow_blockExceptionKeptCleanupSuppressed() =
        runTest {
            val blockError = UncopyableException("block", 1)
            val cleanupError = UncopyableException("cleanup", 2)

            val thrown =
                assertFailsWith<UncopyableException> {
                    withCleanup(cleanup = { throw cleanupError }) { throw blockError }
                }

            assertSame(blockError, thrown)
            assertTrue(thrown.suppressedExceptions.any { it === cleanupError })
        }

    @Test
    fun withCleanup_onlyCleanupThrows_cleanupExceptionPropagates() =
        runTest {
            val cleanupError = UncopyableException("cleanup", 2)

            val thrown =
                assertFailsWith<UncopyableException> {
                    withCleanup(cleanup = { throw cleanupError }) { 1 }
                }

            assertSame(cleanupError, thrown)
        }

    @Test
    fun withCleanup_sameBlockAndCleanupFailure_isNotSuppressedOntoItself() =
        runTest {
            val failure = UncopyableException("same", 1)

            val thrown =
                assertFailsWith<UncopyableException> {
                    withCleanup(cleanup = { throw failure }) { throw failure }
                }

            assertSame(failure, thrown)
            assertTrue(thrown.suppressedExceptions.isEmpty())
        }

    // endregion
}
