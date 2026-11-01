@file:OptIn(ExperimentalStructuredCoroutines::class, InternalCoroutinesApi::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Queues every block and tells plain dispatches apart from yield dispatches. */
private class YieldAwareDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()
    var plainDispatches = 0
    var yieldDispatches = 0

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        plainDispatches++
        queue.addLast(block)
    }

    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        yieldDispatches++
        queue.addLast(block)
    }

    fun drain() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }
}

class DispatchYieldForwardingTest {

    private suspend fun CoroutineScope.runYielding(dispatcher: CoroutineDispatcher, drain: () -> Unit) {
        val job = launch(dispatcher, start = CoroutineStart.DEFAULT) { yield() }
        drain()
        job.join()
    }

    @Test
    fun tracingDispatcherForwardsYieldToTheDelegate() = runTest {
        val delegate = YieldAwareDispatcher()
        val traced = TracingDispatcher(delegate, "d", RingBufferSink(64))

        runYielding(traced, delegate::drain)

        assertEquals(1, delegate.yieldDispatches, "yield() must reach delegate.dispatchYield")
    }

    @Test
    fun yieldDispatchIsStillTraced() = runTest {
        val delegate = YieldAwareDispatcher()
        val sink = RingBufferSink(64)
        val traced = TracingDispatcher(delegate, "d", sink)

        runYielding(traced, delegate::drain)

        val queued = sink.snapshot().count { it is TraceEvent.Queued }
        val finished = sink.snapshot().count { it is TraceEvent.Finished }
        assertEquals(delegate.plainDispatches + delegate.yieldDispatches, queued)
        assertEquals(queued, finished)
        assertTrue(queued >= 2, "the initial dispatch and the yield dispatch are both observed")
    }

    @Test
    fun instrumentedDispatcherForwardsYieldToTheDelegate() = runTest {
        val delegate = YieldAwareDispatcher()
        val instrumented = InstrumentedDispatcher(delegate, "d")

        runYielding(instrumented, delegate::drain)

        assertEquals(1, delegate.yieldDispatches)
        assertEquals(2L, instrumented.snapshot().totalDispatched)
    }
}
