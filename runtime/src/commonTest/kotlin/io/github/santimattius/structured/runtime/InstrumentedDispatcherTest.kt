package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

/** Delegate that queues blocks and runs them only when told to. */
private class ManualDispatcher : CoroutineDispatcher() {
    val queue = ArrayDeque<Runnable>()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }
    fun runNext() = queue.removeFirst().run()
}

@OptIn(ExperimentalStructuredCoroutines::class)
private class MainlessProvider : DispatcherProvider {
    var mainReads = 0
    val manual = ManualDispatcher()
    override val main: CoroutineDispatcher get() { mainReads++; throw IllegalStateException("no main") }
    override val io: CoroutineDispatcher get() = manual
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}

@OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class, ExperimentalTime::class)
class InstrumentedDispatcherTest {

    private fun noop(): Runnable = Runnable { }

    @Test
    fun snapshot_startsEmptyAndCarriesLabel() {
        val d = InstrumentedDispatcher(ManualDispatcher(), "x")
        assertEquals(DispatcherStats("x", 0L, 0, Duration.ZERO), d.snapshot())
        assertEquals("x", d.label)
    }

    @Test
    fun totalDispatched_countsEveryDispatch() {
        val manual = ManualDispatcher()
        val d = InstrumentedDispatcher(manual, "x")
        repeat(3) { d.dispatch(d, noop()) }
        assertEquals(3L, d.snapshot().totalDispatched)
        assertEquals(3, manual.queue.size)
    }

    @Test
    fun peakConcurrency_reflectsSimultaneouslyRunningBlocks() {
        val manual = ManualDispatcher()
        val d = InstrumentedDispatcher(manual, "x")
        // The first block runs the second one nested, so both are "running" at once.
        d.dispatch(d, Runnable { manual.runNext() })
        d.dispatch(d, noop())
        manual.runNext()
        assertEquals(2, d.snapshot().peakConcurrency)
    }

    @Test
    fun peakConcurrency_sequentialBlocksStayAtOne() {
        val manual = ManualDispatcher()
        val d = InstrumentedDispatcher(manual, "x")
        repeat(3) { d.dispatch(d, noop()) }
        repeat(3) { manual.runNext() }
        assertEquals(1, d.snapshot().peakConcurrency)
    }

    @Test
    fun concurrencyIsReleasedEvenWhenBlockThrows() {
        val manual = ManualDispatcher()
        val d = InstrumentedDispatcher(manual, "x")
        d.dispatch(d, Runnable { error("boom") })
        assertFailsWith<IllegalStateException> { manual.runNext() }
        d.dispatch(d, noop())
        manual.runNext()
        assertEquals(1, d.snapshot().peakConcurrency)
    }

    @Test
    fun maxQueueDelay_measuresTimeBetweenDispatchAndStart() {
        val clock = TestTimeSource()
        val manual = ManualDispatcher()
        val d = InstrumentedDispatcher(manual, "x", clock)
        d.dispatch(d, noop())
        clock += 30.milliseconds
        d.dispatch(d, noop())
        clock += 20.milliseconds
        manual.runNext() // waited 50ms
        manual.runNext() // waited 20ms
        assertEquals(50.milliseconds, d.snapshot().maxQueueDelay)
    }

    @Test
    fun reset_zeroesStats() {
        val manual = ManualDispatcher()
        val clock = TestTimeSource()
        val d = InstrumentedDispatcher(manual, "x", clock)
        d.dispatch(d, noop())
        clock += 5.milliseconds
        manual.runNext()
        d.reset()
        assertEquals(DispatcherStats("x", 0L, 0, Duration.ZERO), d.snapshot())
    }

    @Test
    fun isDispatchNeeded_delegates() {
        assertFalse(InstrumentedDispatcher(Dispatchers.Unconfined, "u").isDispatchNeeded(Dispatchers.Unconfined))
        assertTrue(InstrumentedDispatcher(ManualDispatcher(), "m").isDispatchNeeded(Dispatchers.Unconfined))
    }

    @Test
    fun worksAsARealDispatcherWithVirtualTime() = runTest {
        val d = InstrumentedDispatcher(StandardTestDispatcher(testScheduler), "t")
        var done = false
        launch(d) { done = true }
        advanceUntilIdle()
        assertTrue(done)
        assertTrue(d.snapshot().totalDispatched >= 1L)
    }

    @Test
    fun instrumented_labelsAndDoesNotTouchMainAtConstruction() {
        val base = MainlessProvider()
        val p = base.instrumented()
        assertEquals(0, base.mainReads)
        assertEquals(emptyList(), p.stats())
        assertEquals("io", (p.io as InstrumentedDispatcher).label)
        assertEquals("default", (p.default as InstrumentedDispatcher).label)
        assertEquals("unconfined", (p.unconfined as InstrumentedDispatcher).label)
        assertEquals(0, base.mainReads)
    }

    @Test
    fun instrumented_cachesWrapperPerProperty() {
        val p = MainlessProvider().instrumented()
        assertSame(p.io, p.io)
    }

    @Test
    fun instrumented_mainIsLabelledWhenAvailable() {
        val p = DispatcherProvider.of(Dispatchers.Unconfined).instrumented()
        assertEquals("main", (p.main as InstrumentedDispatcher).label)
    }

    @Test
    fun stats_listsOnlyMaterializedDispatchers() {
        val base = MainlessProvider()
        val p = base.instrumented()
        p.io
        p.unconfined
        assertEquals(setOf("io", "unconfined"), p.stats().map { it.label }.toSet())
    }

    @Test
    fun instrumentedProvider_runsWorkOnTheUnderlyingDispatcherAndCounts() = runTest {
        val p = DispatcherProvider.of(StandardTestDispatcher(testScheduler)).instrumented()
        assertEquals(5, withContext(p.io) { 5 })
        assertTrue(p.stats().single { it.label == "io" }.totalDispatched >= 1L)
    }

    @Test
    fun providerReset_zeroesAllMaterializedStats() {
        val base = MainlessProvider()
        val p = base.instrumented()
        p.io.dispatch(p.io, noop())
        assertEquals(1L, p.stats().single().totalDispatched)
        p.reset()
        assertEquals(0L, p.stats().single().totalDispatched)
    }
}
