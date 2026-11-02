@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.tracing

import androidx.test.platform.app.InstrumentationRegistry
import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink
import androidx.tracing.wire.protos.MutableTrace
import io.github.santimattius.structured.runtime.DefaultDispatcherProvider
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.traced
import io.github.santimattius.structured.runtime.withTraceSpan
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs `PerfettoTraceSink` on a real Android runtime with the Android `androidx.tracing` driver
 * (which needs a Context) and decodes the resulting Perfetto file.
 */
class AndroidxTracingDeviceTest {
    @Test
    fun spansProduceSlicesWithArgsAndFlowsOnAndroid() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.cacheDir, "structured-device-test").also { it.deleteRecursively(); it.mkdirs() }

        val driver =
            TraceDriver(
                context = context,
                sink = TraceSink(context, 1, Dispatchers.IO + NonCancellable) { File(dir, "device.perfetto-trace") },
                // The default only enables categories while a system trace is recording.
                isCategoryEnabled = { true },
            )
        driver.use {
            val provider = DefaultDispatcherProvider.traced(PerfettoTraceSink(driver.tracer))
            runBlocking {
                withTraceSpan("checkout") {
                    coroutineScope {
                        (1..4)
                            .map { i ->
                                async(provider.io + CoroutineName("fetch")) {
                                    Thread.sleep(2)
                                    i
                                }
                            }.awaitAll()
                    }
                }
            }
        }

        val file = checkNotNull(dir.listFiles()?.singleOrNull()) { "expected one trace file in $dir, found ${dir.listFiles()?.map { it.name + ":" + it.length() }}, enabled=${driver.tracer.isCategoryEnabled("structured")}" }
        val events =
            MutableTrace.ADAPTER
                .decode(file.readBytes())
                .packet
                .mapNotNull { it.track_event }
        val slices = events.filter { it.name == "io:fetch" }
        assertTrue(slices.isNotEmpty(), "expected io:fetch slices, got ${events.map { it.name }.distinct()}")
        val annotations = slices.flatMap { it.debug_annotations }.map { it.name }.toSet()
        assertTrue("queue_delay_ns" in annotations, "annotations: $annotations")
        assertTrue("span_name" in annotations, "annotations: $annotations")
        assertTrue(slices.any { it.flow_ids.isNotEmpty() }, "no flows on slices")
        assertEquals(1, slices.map { s -> s.debug_annotations.first { it.name == "span_name" }.string_value }.toSet().size)
    }
}
