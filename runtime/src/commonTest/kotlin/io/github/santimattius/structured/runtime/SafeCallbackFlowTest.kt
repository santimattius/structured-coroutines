package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalStructuredCoroutines::class)
class SafeCallbackFlowTest {

    @Test
    fun emissionsArriveInOrder() = runTest {
        val flow = safeCallbackFlow<Int> { channel ->
            channel.trySend(1)
            channel.trySend(2)
            channel.trySend(3)
            DisposableHandle { }
        }

        val result = flow.take(3).toList()

        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun registerThrows_exceptionSurfacesAndNoDisposeAttempted() = runTest {
        var disposeCalled = false

        val flow = safeCallbackFlow<Int> {
            throw IllegalStateException("register failed")
        }

        val exception = assertFailsWith<IllegalStateException> {
            flow.collect { }
        }

        assertEquals("register failed", exception.message)
        assertFalse(disposeCalled)
    }

    @Test
    fun collectorCancelled_disposeRunsExactlyOnce() = runTest {
        var disposeCalls = 0
        val disposed = CompletableDeferred<Unit>()

        val flow = safeCallbackFlow<Int> { _ ->
            DisposableHandle {
                disposeCalls++
                disposed.complete(Unit)
            }
        }

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            flow.collect { }
        }

        job.cancelAndJoin()
        disposed.await()

        assertEquals(1, disposeCalls)
    }
}
