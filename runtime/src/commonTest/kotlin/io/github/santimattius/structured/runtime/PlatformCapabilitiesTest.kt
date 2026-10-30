@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Records only dispatcher labels: no thread identity, no span links, no counters. */
private class BareSink : TraceSink {
    val labels = ArrayList<String>()
    var finished = 0
    override fun emit(event: TraceEvent) {
        when (event) {
            is TraceEvent.Started -> labels.add(event.dispatcher)
            is TraceEvent.Finished -> finished++
            else -> Unit
        }
    }
}

class PlatformCapabilitiesTest {

    @Test
    fun capabilitiesAreReadableOnEveryTarget() {
        val capabilities = platformCapabilities
        // Reading must never throw; the values themselves are target specific.
        assertEquals(capabilities, platformCapabilities)
    }

    @Test
    fun platformTraceSinkNeverThrows() {
        val sink = platformTraceSink()
        // Null is the degraded answer; a sink must at least answer isEnabled without failing.
        sink?.isEnabled(TraceLevel.Dispatch)
    }

    @Test
    fun systemTraceIntegrationImpliesASink() {
        if (platformCapabilities.systemTraceIntegration) {
            assertTrue(platformTraceSink() != null)
        }
    }

    @Test
    fun noSinkMeansNoSystemTraceIntegration() {
        if (platformTraceSink() == null) {
            assertEquals(false, platformCapabilities.systemTraceIntegration)
            assertEquals(false, platformCapabilities.counters)
            assertEquals(false, platformCapabilities.flows)
        }
    }

    @Test
    fun capabilitiesHaveValueSemantics() {
        val a = TraceCapabilities(threadIdentity = true, systemTraceIntegration = false, counters = true, flows = false)
        val b = TraceCapabilities(threadIdentity = true, systemTraceIntegration = false, counters = true, flows = false)
        val c = TraceCapabilities(threadIdentity = false, systemTraceIntegration = false, counters = true, flows = false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
        assertEquals(
            "TraceCapabilities(threadIdentity=true, systemTraceIntegration=false, counters=true, flows=false)",
            a.toString(),
        )
    }

    @Test
    fun aSinkWithoutThreadIdentityOrFlowsStillWorksThroughTraced() = runTest {
        val sink = BareSink()
        val dispatchers = DispatcherProvider.of(StandardTestDispatcher(testScheduler)).traced(sink)

        val result = withContext(dispatchers.io) { 21 * 2 }

        assertEquals(42, result)
        assertEquals(listOf("io"), sink.labels)
        assertEquals(1, sink.finished)
    }
}
