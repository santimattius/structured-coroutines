package io.github.santimattius.structured.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class InstrumentedDispatcherOnTracingTest {

    @Test
    fun resumeAfterDelay_isCountedExactlyOnceAndAdvancesVirtualTimeExactly() = runTest {
        val d = InstrumentedDispatcher(StandardTestDispatcher(testScheduler), "t")
        val job = launch(d) { delay(1000) }
        runCurrent() // the launch itself: one dispatch, now suspended in delay
        assertEquals(1L, d.snapshot().totalDispatched)
        val start = currentTime
        advanceUntilIdle() // timer fires, resume dispatches once
        assertEquals(1000L, currentTime - start)
        assertEquals(2L, d.snapshot().totalDispatched) // launch + exactly one resume
        assertEquals(true, job.isCompleted)
    }

    @Test
    fun matchesAHandBuiltTracingDispatcherWithStatsSink() = runTest {
        val sink = StatsSink(maxLevel = TraceLevel.Dispatch)
        val manual = StandardTestDispatcher(testScheduler)
        val instrumented = InstrumentedDispatcher(manual, "x")
        val traced = TracingDispatcher(manual, "x", sink)
        suspend fun workload(d: kotlinx.coroutines.CoroutineDispatcher) {
            withContext(d) {
                repeat(3) { launch { delay(10) } }
                delay(5)
            }
        }
        workload(instrumented)
        workload(traced)
        val expected = sink.metrics("x")!!
        val actual = instrumented.snapshot()
        assertEquals(expected.queued, actual.totalDispatched)
        assertEquals(expected.peakRunning, actual.peakConcurrency)
    }
}
