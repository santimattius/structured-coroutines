@file:OptIn(ExperimentalStructuredCoroutines::class, ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.ExperimentalTime
import kotlin.time.TestTimeSource

private class CollectingSink(var enabled: Boolean = true) : TraceSink {
    val events = ArrayList<TraceEvent>()
    override fun emit(event: TraceEvent) {
        events.add(event)
    }
    override fun isEnabled(level: TraceLevel): Boolean = enabled
    fun samples(): List<TraceEvent.PoolSample> = events.filterIsInstance<TraceEvent.PoolSample>()
}

private class ThrowingSink : TraceSink {
    var emits = 0
    override fun emit(event: TraceEvent) {
        emits++
        throw IllegalStateException("boom")
    }
}

private fun statsWith(
    label: String,
    queued: Int,
    started: Int,
    finished: Int,
    stats: StatsSink = StatsSink(),
): StatsSink {
    repeat(queued) { stats.emit(TraceEvent.Queued(label, 0, null)) }
    repeat(started) { stats.emit(TraceEvent.Started(label, 0, 0, null, null)) }
    repeat(finished) { stats.emit(TraceEvent.Finished(label, 0, 0, TraceOutcome.Completed, null, null)) }
    return stats
}

class PoolSamplerTest {

    private val clock = TestTimeSource()

    @Test
    fun sampleOnceEmitsOneSamplePerLabelWithCountersAndTimestamp() {
        val stats = statsWith("io", queued = 5, started = 4, finished = 1)
        statsWith("default", queued = 2, started = 2, finished = 2, stats = stats)
        val sink = CollectingSink()
        val sampler = PoolSampler(stats, sink, { null }, clock)
        clock += 250.nanoseconds
        sampler.sampleOnce()
        val samples = sink.samples()
        assertEquals(2, samples.size)
        assertEquals(listOf("default", "io"), samples.map { it.dispatcher }.sorted())
        val io = samples.single { it.dispatcher == "io" }
        assertEquals(5L, io.queued)
        assertEquals(4L, io.started)
        assertEquals(1L, io.finished)
        assertEquals(1L, io.pending)
        assertEquals(3, io.running)
        assertEquals(4, io.peakRunning)
        assertEquals(250L, io.timestampNanos)
        assertEquals(TraceLevel.Pool, io.level)
        assertNull(io.parallelism)
    }

    @Test
    fun sampleOnceWithNoLabelsEmitsNothing() {
        val sink = CollectingSink()
        PoolSampler(StatsSink(), sink, { null }, clock).sampleOnce()
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun sampleOnceEmitsNothingWhenPoolLevelDisabled() {
        val stats = statsWith("io", 1, 1, 0)
        val sink = CollectingSink(enabled = false)
        PoolSampler(stats, sink, { null }, clock).sampleOnce()
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun faultyEnabledCheckNeverPropagates() {
        val stats = statsWith("io", 1, 1, 0)
        val sink = object : TraceSink {
            override fun emit(event: TraceEvent) = Unit
            override fun isEnabled(level: TraceLevel): Boolean = throw IllegalStateException("boom")
        }
        PoolSampler(stats, sink, { null }, clock).sampleOnce()
    }

    @Test
    fun faultySinkNeverPropagatesAndOtherLabelsAreStillSampled() {
        val stats = statsWith("io", 1, 1, 0)
        statsWith("default", 1, 1, 0, stats)
        val sink = ThrowingSink()
        PoolSampler(stats, sink, { null }, clock).sampleOnce()
        assertEquals(2, sink.emits)
    }

    @Test
    fun parallelismComesFromTheLambdaPerLabel() {
        val stats = statsWith("io", 1, 1, 0)
        statsWith("default", 1, 1, 0, stats)
        val sink = CollectingSink()
        PoolSampler(stats, sink, { label -> if (label == "io") 64 else null }, clock).sampleOnce()
        assertEquals(64, sink.samples().single { it.dispatcher == "io" }.parallelism)
        assertNull(sink.samples().single { it.dispatcher == "default" }.parallelism)
    }

    @Test
    fun publicConstructorDefaultsParallelismToNull() {
        val stats = statsWith("io", 1, 1, 0)
        val sink = CollectingSink()
        PoolSampler(stats, sink).sampleOnce()
        assertNull(sink.samples().single().parallelism)
    }

    @Test
    fun launchInSamplesWithExactCadence() = runTest {
        val stats = statsWith("io", 1, 1, 0)
        val sink = CollectingSink()
        val sampler = PoolSampler(stats, sink, { null }, clock)
        val job = sampler.launchIn(backgroundScope, 100.milliseconds)
        runCurrent()
        assertEquals(1, sink.samples().size) // immediately at t=0
        advanceTimeBy(100.milliseconds)
        assertEquals(1, sink.samples().size) // t=100 is not yet run
        runCurrent()
        assertEquals(2, sink.samples().size)
        advanceTimeBy(250.milliseconds)
        runCurrent()
        assertEquals(4, sink.samples().size) // t=0,100,200,300 (350 reached)
        assertTrue(job.isActive)
    }

    @Test
    fun launchInStopsWhenCallerScopeIsCancelled() = runTest {
        val sink = CollectingSink()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val job = PoolSampler(statsWith("io", 1, 1, 0), sink, { null }, clock)
            .launchIn(scope, 100.milliseconds)
        runCurrent()
        assertEquals(1, sink.samples().size)
        scope.cancel()
        advanceTimeBy(1_000.milliseconds)
        runCurrent()
        assertEquals(1, sink.samples().size)
        assertTrue(job.isCancelled)
    }

    @Test
    fun returnedJobCanBeCancelled() = runTest {
        val sink = CollectingSink()
        val job = PoolSampler(statsWith("io", 1, 1, 0), sink, { null }, clock)
            .launchIn(backgroundScope, 100.milliseconds)
        runCurrent()
        job.cancel()
        advanceTimeBy(1_000.milliseconds)
        runCurrent()
        assertEquals(1, sink.samples().size)
        assertFalse(job.isActive)
    }

    @Test
    fun launchInRejectsNonPositiveInterval() = runTest {
        val sampler = PoolSampler(StatsSink(), CollectingSink(), { null }, clock)
        assertFailsWith<IllegalArgumentException> { sampler.launchIn(backgroundScope, Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { sampler.launchIn(backgroundScope, (-1).milliseconds) }
    }

    @Test
    fun nothingIsStartedImplicitly() = runTest {
        val sink = CollectingSink()
        PoolSampler(statsWith("io", 1, 1, 0), sink, { null }, clock)
        advanceTimeBy(10_000.milliseconds)
        runCurrent()
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun poolSampleEqualityAndToString() {
        fun sample(parallelism: Int? = 4) =
            TraceEvent.PoolSample("io", 10, 5, 4, 1, 1, 3, 4, parallelism)
        assertEquals(sample(), sample())
        assertEquals(sample().hashCode(), sample().hashCode())
        assertFalse(sample() == sample(null))
        val text = sample().toString()
        assertTrue(text.startsWith("PoolSample(dispatcher=io, timestampNanos=10"), text)
        assertTrue("parallelism=4" in text, text)
        assertIs<TraceEvent>(sample())
        assertEquals(TraceLevel.Pool, sample().level)
    }
}
