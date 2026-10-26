@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.tracing

import androidx.tracing.Tracer
import androidx.tracing.wire.protos.MutableTrace
import androidx.tracing.wire.protos.MutableTrackEvent
import io.github.santimattius.structured.runtime.DefaultDispatcherProvider
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.traced
import io.github.santimattius.structured.runtime.withTraceSpan
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PerfettoTraceSessionTest {

    private val dir: File = Files.createTempDirectory("perfetto-session").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun decode(file: File): List<MutableTrackEvent> =
        MutableTrace.ADAPTER.decode(file.readBytes()).packet.mapNotNull { it.track_event }

    @Test
    fun tracedDispatchersProduceANonEmptyPerfettoFileWithSlicesAndCounters() {
        val session = PerfettoTraceSession.start(dir)
        val provider = DefaultDispatcherProvider.traced(session.sink)

        runBlocking {
            withTraceSpan("checkout") {
                coroutineScope {
                    (1..4).map { i ->
                        async(provider.io + CoroutineName("fetch")) { Thread.sleep(2); i }
                    }.awaitAll()
                }
                withContext(provider.default + CoroutineName("render")) { Thread.sleep(2) }
            }
        }
        session.close()

        assertTrue(session.file.exists(), "trace file exists")
        assertTrue(session.file.name.endsWith(".perfetto-trace"))
        assertEquals(dir, session.file.parentFile)
        assertTrue(session.file.length() > 0, "trace file is not empty")

        val events = decode(session.file)
        val sliceNames = events.filter { it.type == MutableTrackEvent.Type.TYPE_SLICE_BEGIN }.map { it.name }
        assertTrue("io:fetch" in sliceNames, "slices: $sliceNames")
        assertTrue("default:render" in sliceNames, "slices: $sliceNames")
        assertTrue(
            events.any { it.type == MutableTrackEvent.Type.TYPE_SLICE_END },
            "slices are closed",
        )
        assertTrue(
            events.any { it.type == MutableTrackEvent.Type.TYPE_COUNTER },
            "pending/running counters are written",
        )
        assertTrue(events.any { it.flow_ids.isNotEmpty() }, "span ids draw flows")
    }

    @Test
    fun flushMakesEventsReadableBeforeClose() {
        val session = PerfettoTraceSession.start(dir)
        val provider = DefaultDispatcherProvider.traced(session.sink)
        runBlocking { withContext(provider.io + CoroutineName("early")) { } }

        session.flush()
        val names = decode(session.file).map { it.name }
        session.close()

        assertTrue("io:early" in names, "names: $names")
    }

    @Test
    fun closeIsIdempotentAndLaterEmitsNeverThrow() {
        val session = PerfettoTraceSession.start(dir)
        session.close()
        session.close()
        val provider = DefaultDispatcherProvider.traced(session.sink)
        runBlocking { withContext(provider.io) { } }
        session.flush()
    }

    @Test
    fun theSessionNeverInstallsAGlobalTracer() {
        val session = PerfettoTraceSession.start(dir, maxLevel = TraceLevel.Dispatch)
        try {
            assertSame(Tracer.getStubTracer(), Tracer.global)
        } finally {
            session.close()
        }
        assertFalse(session.tracer === Tracer.global)
    }
}
