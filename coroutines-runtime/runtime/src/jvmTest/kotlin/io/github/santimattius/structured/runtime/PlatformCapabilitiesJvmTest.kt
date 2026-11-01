@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlatformCapabilitiesJvmTest {

    @Test
    fun jvmHasThreadIdentityButNoBackendInRuntime() {
        assertEquals(
            TraceCapabilities(threadIdentity = true, systemTraceIntegration = false, counters = false, flows = false),
            platformCapabilities,
        )
    }

    @Test
    fun jvmShipsNoDefaultSink() {
        assertNull(platformTraceSink())
    }

    @Test
    fun threadIdentityFlagMatchesCurrentThreadInfo() {
        assertEquals(platformCapabilities.threadIdentity, currentThreadInfo() != null)
    }
}
