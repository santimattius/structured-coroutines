@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val poolSample = TraceEvent.PoolSample("io", 1, 2, 1, 0, 1, 1, 1, 8)

class PoolSampleSinksTest {

    @Test
    fun statsSinkIgnoresPoolSample() {
        val stats = StatsSink(TraceLevel.Pool)
        stats.emit(poolSample)
        assertTrue(stats.metrics().isEmpty())
    }

    @Test
    fun loggingSinkFormatsPoolSampleViaToString() {
        val lines = ArrayList<String>()
        LoggingSink(TraceLevel.Pool) { lines.add(it) }.emit(poolSample)
        assertEquals(listOf(poolSample.toString()), lines)
        assertTrue(lines.single().contains("PoolSample(dispatcher=io"))
    }

    @Test
    fun ringBufferKeepsPoolSample() {
        val ring = RingBufferSink(4, TraceLevel.Pool)
        ring.emit(poolSample)
        assertEquals(listOf<TraceEvent>(poolSample), ring.snapshot())
    }

    @Test
    fun compositeSinkDeliversPoolSampleOnlyToPoolEnabledSinks() {
        val pool = RingBufferSink(4, TraceLevel.Pool)
        val dispatchOnly = RingBufferSink(4, TraceLevel.Dispatch)
        CompositeSink(pool, dispatchOnly).emit(poolSample)
        assertEquals(1, pool.snapshot().size)
        assertTrue(dispatchOnly.snapshot().isEmpty())
    }
}
