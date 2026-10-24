package io.github.santimattius.structured.runtime

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class RingBufferSinkConcurrencyTest {

    private fun run(threads: Int, perThread: Int, capacity: Int): RingBufferSink {
        val sink = RingBufferSink(capacity)
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = java.util.concurrent.ConcurrentLinkedQueue<Throwable>()
        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    repeat(perThread) { i ->
                        sink.emit(TraceEvent.Queued("t$t", i.toLong(), null))
                    }
                } catch (e: Throwable) {
                    failures += e
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertTrue(failures.isEmpty(), "emit threw: $failures")
        return sink
    }

    @Test
    fun snapshotSizeIsMinOfTotalAndCapacity() {
        assertEquals(64, run(8, 5_000, 64).snapshot().size)
        assertEquals(40, run(8, 5, 64).snapshot().size)
    }

    @Test
    fun perThreadOrderIsPreserved() {
        val snap = run(8, 5_000, 256).snapshot().map { it as TraceEvent.Queued }
        snap.groupBy { it.dispatcher }.values.forEach { events ->
            val stamps = events.map { it.timestampNanos }
            assertEquals(stamps.sorted(), stamps)
        }
    }

    @Test
    fun noDuplicatesInSnapshot() {
        val snap = run(8, 5_000, 128).snapshot()
        assertEquals(snap.size, snap.map { (it as TraceEvent.Queued).let { q -> q.dispatcher to q.timestampNanos } }.toSet().size)
    }
}
