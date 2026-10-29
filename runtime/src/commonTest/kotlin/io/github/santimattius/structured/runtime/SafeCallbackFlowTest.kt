package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun unlimitedBuffer_acceptsEveryCallbackEmission() = runTest {
        var accepted = 0
        val flow = safeCallbackFlow<Int>(buffer = Channel.UNLIMITED) { channel ->
            repeat(500) { if (channel.trySend(it).isSuccess) accepted++ }
            channel.close()
            DisposableHandle { }
        }

        val result = flow.toList()

        assertEquals(500, accepted)
        assertEquals((0 until 500).toList(), result)
    }

    @Test
    fun dropOldest_keepsTheLatestValuesWhenTheCollectorIsSlow() = runTest {
        val flow = safeCallbackFlow<Int>(buffer = 2, onBufferOverflow = BufferOverflow.DROP_OLDEST) { channel ->
            repeat(5) { channel.trySend(it + 1) }
            channel.close()
            DisposableHandle { }
        }

        val result = flow.toList()

        assertEquals(5, result.last(), "the most recent value must survive")
        assertTrue(result.size < 5, "older values are dropped, got $result")
    }

    @Test
    fun closingTheChannelFromTheCallback_completesTheFlowAndDisposes() = runTest {
        var disposed = 0
        val flow = safeCallbackFlow<Int> { channel ->
            channel.trySend(1)
            channel.close()
            DisposableHandle { disposed++ }
        }

        assertEquals(listOf(1), flow.toList())
        assertEquals(1, disposed)
    }
}
