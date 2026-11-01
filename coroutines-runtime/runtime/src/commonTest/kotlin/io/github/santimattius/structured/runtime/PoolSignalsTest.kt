@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun sample(
    label: String = "io",
    pending: Long = 0,
    running: Int = 0,
    parallelism: Int? = null,
    at: Long = 0,
) = TraceEvent.PoolSample(label, at, 0, 0, 0, pending, running, running, parallelism)

private fun pendings(label: String = "io", vararg pending: Long) =
    pending.mapIndexed { i, p -> sample(label, pending = p, at = i.toLong()) }

class PoolSignalsTest {

    @Test
    fun backlogGrowingWhenPendingNonDecreasingAndRises() {
        val signals = pendings("io", 1, 2, 2, 5).signals()
        val s = signals.single()
        assertIs<PoolSignal.BacklogGrowing>(s)
        assertEquals("io", s.label)
        assertEquals(4, s.samples)
        assertEquals(1L, s.from)
        assertEquals(5L, s.to)
    }

    @Test
    fun noBacklogWhenPendingDecreasesAnywhere() {
        assertTrue(pendings("io", 1, 3, 2, 5).signals().isEmpty())
    }

    @Test
    fun noBacklogWhenFlat() {
        assertTrue(pendings("io", 3, 3, 3, 3).signals().isEmpty())
        assertTrue(pendings("io", 0, 0, 0, 0).signals().isEmpty())
    }

    @Test
    fun noSignalWithFewerSamplesThanMinSamples() {
        assertTrue(pendings("io", 1, 2, 3).signals().isEmpty())
        assertTrue(pendings("io", 1, 2, 3).signals(minSamples = 4).isEmpty())
        assertEquals(1, pendings("io", 1, 2, 3).signals(minSamples = 3).size)
    }

    @Test
    fun onlyTheLastMinSamplesAreEvaluated() {
        // An old decrease is outside the window; the last four grow.
        val signals = pendings("io", 9, 1, 2, 3, 4).signals()
        val s = signals.single() as PoolSignal.BacklogGrowing
        assertEquals(1L, s.from)
        assertEquals(4L, s.to)
        // A recent decrease inside the window cancels it.
        assertTrue(pendings("io", 1, 2, 3, 4, 3).signals().isEmpty())
    }

    @Test
    fun labelsAreEvaluatedIndependentlyEvenWhenInterleaved() {
        val input = listOf(
            sample("io", pending = 1, at = 0), sample("default", pending = 5, at = 0),
            sample("io", pending = 2, at = 1), sample("default", pending = 4, at = 1),
            sample("io", pending = 3, at = 2), sample("default", pending = 3, at = 2),
            sample("io", pending = 4, at = 3), sample("default", pending = 2, at = 3),
        )
        val signals = input.signals()
        assertEquals(listOf("io"), signals.map { it.label })
    }

    @Test
    fun poolSaturatedWhenRunningAtLimitAndPendingInAll() {
        val input = (0 until 4).map { sample(pending = 2, running = 8, parallelism = 8, at = it.toLong()) }
        val s = input.signals().single()
        assertIs<PoolSignal.PoolSaturated>(s)
        assertEquals("io", s.label)
        assertEquals(8, s.parallelism)
        assertEquals(4, s.samples)
    }

    @Test
    fun poolSaturatedAlsoWhenRunningExceedsParallelism() {
        val input = (0 until 4).map { sample(pending = 1, running = 9, parallelism = 8) }
        assertIs<PoolSignal.PoolSaturated>(input.signals().single())
    }

    @Test
    fun notSaturatedWhenRunningBelowLimitInAnySample() {
        val input = listOf(8, 8, 7, 8).map { sample(pending = 2, running = it, parallelism = 8) }
        assertTrue(input.signals().isEmpty())
    }

    @Test
    fun notSaturatedWhenPendingZeroInAnySample() {
        val input = listOf(1L, 0L, 1L, 1L).map { sample(pending = it, running = 8, parallelism = 8) }
        assertTrue(input.signals().none { it is PoolSignal.PoolSaturated })
    }

    @Test
    fun notSaturatedWhenParallelismUnknown() {
        val input = (0 until 4).map { sample(pending = 2, running = 8, parallelism = null) }
        assertTrue(input.signals().isEmpty())
    }

    @Test
    fun bothSignalsCanFireTogether() {
        val input = listOf(1L, 2L, 3L, 4L).mapIndexed { i, p ->
            sample(pending = p, running = 4, parallelism = 4, at = i.toLong())
        }
        val signals = input.signals()
        assertEquals(2, signals.size)
        assertTrue(signals.any { it is PoolSignal.BacklogGrowing })
        assertTrue(signals.any { it is PoolSignal.PoolSaturated })
    }

    @Test
    fun emptyInputYieldsNoSignals() {
        assertTrue(emptyList<TraceEvent.PoolSample>().signals().isEmpty())
    }

    @Test
    fun minSamplesMustBeAtLeastTwo() {
        assertFailsWith<IllegalArgumentException> { emptyList<TraceEvent.PoolSample>().signals(minSamples = 1) }
        assertFailsWith<IllegalArgumentException> { emptyList<TraceEvent.PoolSample>().signals(minSamples = 0) }
        assertEquals(1, pendings("io", 1, 2).signals(minSamples = 2).size)
    }

    @Test
    fun newSignalsHaveValueSemantics() {
        assertEquals(PoolSignal.BacklogGrowing("io", 4, 1, 5), PoolSignal.BacklogGrowing("io", 4, 1, 5))
        assertEquals(
            PoolSignal.BacklogGrowing("io", 4, 1, 5).hashCode(),
            PoolSignal.BacklogGrowing("io", 4, 1, 5).hashCode(),
        )
        assertEquals(PoolSignal.PoolSaturated("io", 8, 4), PoolSignal.PoolSaturated("io", 8, 4))
        assertTrue(PoolSignal.BacklogGrowing("io", 4, 1, 5).toString().contains("from=1"))
        assertTrue(PoolSignal.PoolSaturated("io", 8, 4).toString().contains("parallelism=8"))
    }
}
