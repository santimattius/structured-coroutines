package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class FlowSafetyTest {
    // ---- launchInSafely ----

    @Test
    fun launchInSafely_upstreamException_goesToOnError_andParentScopeSurvives() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + parent)
            val boom = IllegalStateException("boom")
            val errors = mutableListOf<Throwable>()

            flow<Int> { throw boom }.launchInSafely(scope) { errors += it }
            advanceUntilIdle()

            assertEquals(1, errors.size)
            assertSame(boom, errors.single())
            assertTrue(parent.isActive)
            parent.cancel()
        }

    @Test
    fun launchInSafely_valuesBeforeError_areStillCollected() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + parent)
            val seen = mutableListOf<Int>()
            val errors = mutableListOf<Throwable>()

            flow {
                emit(1)
                emit(2)
                throw IllegalStateException("boom")
            }.onEach { seen += it }.launchInSafely(scope) { errors += it }
            advanceUntilIdle()

            assertEquals(listOf(1, 2), seen)
            assertEquals(1, errors.size)
            parent.cancel()
        }

    @Test
    fun launchInSafely_upstreamCancellationException_isRethrownWithoutCallingOnError() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + parent)
            val cancellation = CancellationException("upstream")
            val errors = mutableListOf<Throwable>()

            val job = flow<Int> { throw cancellation }.launchInSafely(scope) { errors += it }
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertTrue(errors.isEmpty())
            assertTrue(parent.isActive)
            parent.cancel()
        }

    @Test
    fun launchInSafely_success_neverCallsOnError() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + parent)
            val seen = mutableListOf<Int>()
            var errorCalls = 0

            val job =
                flow {
                    emit(1)
                    emit(2)
                }.onEach { seen += it }
                    .launchInSafely(scope) { errorCalls++ }
            advanceUntilIdle()

            assertEquals(listOf(1, 2), seen)
            assertEquals(0, errorCalls)
            assertTrue(job.isCompleted)
            parent.cancel()
        }

    @Test
    fun launchInSafely_scopeCancellation_doesNotCallOnError() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + parent)
            var errorCalls = 0
            val shared = MutableSharedFlow<Int>()

            val job = shared.launchInSafely(scope) { errorCalls++ }
            advanceUntilIdle()
            parent.cancel()
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertEquals(0, errorCalls)
        }

    @Test
    fun launchInSafely_cancelDuringUpstreamDelay_doesNotCallOnError() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + parent)
            var errorCalls = 0

            val job =
                flow<Int> {
                    delay(1_000)
                    emit(1)
                }.launchInSafely(scope) { errorCalls++ }
            advanceTimeBy(500)
            job.cancel()
            advanceUntilIdle()

            assertTrue(job.isCancelled)
            assertEquals(0, errorCalls)
            parent.cancel()
        }

    @Test
    fun launchInSafely_returnedJob_canBeCancelled_andStopsCollection() =
        runTest {
            val parent = Job()
            val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + parent)
            val shared = MutableSharedFlow<Int>()
            val seen = mutableListOf<Int>()

            val job = shared.onEach { seen += it }.launchInSafely(scope) { }
            advanceUntilIdle()
            shared.emit(1)
            job.cancel()
            advanceUntilIdle()
            assertEquals(0, shared.subscriptionCount.value)

            assertTrue(job.isCancelled)
            assertEquals(listOf(1), seen)
            parent.cancel()
        }

    // ---- stateInWhileSubscribed ----

    private class Upstream {
        var starts = 0
        var active = false
        val flow: Flow<Int> =
            flow {
                starts++
                active = true
                try {
                    emit(1)
                    delay(Long.MAX_VALUE)
                } finally {
                    active = false
                }
            }
    }

    private fun TestScope.sharingScope(): CoroutineScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job())

    @Test
    fun stateInWhileSubscribed_doesNotStartUpstreamUntilSubscribed() =
        runTest {
            val upstream = Upstream()
            val scope = sharingScope()

            val state = upstream.flow.stateInWhileSubscribed(scope, initialValue = 0)
            advanceUntilIdle()
            assertEquals(0, upstream.starts)

            val sub = scope.launch { state.collect { } }
            runCurrent()
            assertEquals(1, upstream.starts)
            sub.cancel()
            scope.coroutineContext[Job]!!.cancel()
        }

    @Test
    fun stateInWhileSubscribed_initialValueVisibleBeforeFirstEmission() =
        runTest {
            val scope = sharingScope()
            val state = flow<Int> { delay(Long.MAX_VALUE) }.stateInWhileSubscribed(scope, initialValue = 7)

            assertEquals(7, state.value)

            val sub = scope.launch { state.collect { } }
            runCurrent()
            assertEquals(7, state.value)
            sub.cancel()
            scope.coroutineContext[Job]!!.cancel()
        }

    @Test
    fun stateInWhileSubscribed_firstEmissionReplacesInitialValue() =
        runTest {
            val upstream = Upstream()
            val scope = sharingScope()
            val state = upstream.flow.stateInWhileSubscribed(scope, initialValue = 0)

            val sub = scope.launch { state.collect { } }
            runCurrent()

            assertEquals(1, state.value)
            sub.cancel()
            scope.coroutineContext[Job]!!.cancel()
        }

    @Test
    fun stateInWhileSubscribed_staysActiveWithinTimeout_thenStops() =
        runTest {
            val upstream = Upstream()
            val scope = sharingScope()
            val state = upstream.flow.stateInWhileSubscribed(scope, initialValue = 0, stopTimeoutMillis = 5_000)

            val sub = scope.launch { state.collect { } }
            runCurrent()
            assertTrue(upstream.active)

            sub.cancel()
            runCurrent()
            advanceTimeBy(4_999)
            assertTrue(upstream.active, "upstream should stay active within the stop timeout")

            advanceTimeBy(2)
            runCurrent()
            assertFalse(upstream.active, "upstream should stop after the stop timeout")
            scope.coroutineContext[Job]!!.cancel()
        }

    @Test
    fun stateInWhileSubscribed_defaultStopTimeout_isFiveSeconds() =
        runTest {
            val upstream = Upstream()
            val scope = sharingScope()
            val state = upstream.flow.stateInWhileSubscribed(scope, initialValue = 0)

            val sub = scope.launch { state.collect { } }
            runCurrent()
            sub.cancel()
            runCurrent()

            advanceTimeBy(4_999)
            assertTrue(upstream.active)
            advanceTimeBy(2)
            runCurrent()
            assertFalse(upstream.active)
            scope.coroutineContext[Job]!!.cancel()
        }

    @Test
    fun stateInWhileSubscribed_restartsOnResubscriptionAfterStop() =
        runTest {
            val upstream = Upstream()
            val scope = sharingScope()
            val state = upstream.flow.stateInWhileSubscribed(scope, initialValue = 0, stopTimeoutMillis = 1_000)

            val first = scope.launch { state.collect { } }
            runCurrent()
            first.cancel()
            runCurrent()
            advanceTimeBy(1_001)
            runCurrent()
            assertFalse(upstream.active)
            assertEquals(1, upstream.starts)

            val second = scope.launch { state.collect { } }
            runCurrent()

            assertEquals(2, upstream.starts)
            assertTrue(upstream.active)
            second.cancel()
            scope.coroutineContext[Job]!!.cancel()
        }

    @Test
    fun stateInWhileSubscribed_resubscribeWithinTimeout_doesNotRestart() =
        runTest {
            val upstream = Upstream()
            val scope = sharingScope()
            val state = upstream.flow.stateInWhileSubscribed(scope, initialValue = 0, stopTimeoutMillis = 1_000)

            val first = scope.launch { state.collect { } }
            runCurrent()
            first.cancel()
            runCurrent()
            advanceTimeBy(500)

            val second = scope.launch { state.collect { } }
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()

            assertEquals(1, upstream.starts)
            assertTrue(upstream.active)
            second.cancel()
            scope.coroutineContext[Job]!!.cancel()
        }
}
