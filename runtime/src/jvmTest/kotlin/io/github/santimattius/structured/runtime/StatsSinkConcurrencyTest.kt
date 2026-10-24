package io.github.santimattius.structured.runtime

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class StatsSinkConcurrencyTest {

    private val threads = 8
    private val perThread = 10_000

    @Test
    fun countersAreExactAndPeakIsBoundedByOpenPairs() {
        val sink = StatsSink()
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val failures = ConcurrentLinkedQueue<Throwable>()
        repeat(threads) { t ->
            pool.execute {
                try {
                    val info = ThreadInfo(t.toLong(), "w$t")
                    start.await()
                    repeat(perThread) { i ->
                        sink.emit(TraceEvent.Queued("io", i.toLong(), null))
                        // Each thread has at most one open Started/Finished pair at a time.
                        sink.emit(TraceEvent.Started("io", i.toLong(), 1_500L, null, info))
                        sink.emit(TraceEvent.Finished("io", i.toLong(), 2_500L, TraceOutcome.Completed, null, info))
                    }
                } catch (e: Throwable) {
                    failures += e
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(60, TimeUnit.SECONDS))
        pool.shutdownNow()
        assertTrue(failures.isEmpty(), "emit threw: $failures")

        val m = sink.metrics("io")!!
        val total = (threads * perThread).toLong()
        assertEquals(total, m.queued)
        assertEquals(total, m.started)
        assertEquals(total, m.finished)
        assertEquals(total, m.queueDelay.count)
        assertEquals(total, m.runTime.count)
        assertEquals(0, m.running)
        assertEquals(0L, m.pending)
        assertEquals(threads, m.threadsObserved)
        assertTrue(m.peakRunning in 1..threads, "peakRunning=${m.peakRunning}")
    }
}
