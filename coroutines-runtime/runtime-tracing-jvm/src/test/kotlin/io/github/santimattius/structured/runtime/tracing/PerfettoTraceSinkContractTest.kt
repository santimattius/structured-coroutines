@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.tracing

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.TraceSink
import io.github.santimattius.structured.runtime.test.TraceSinkContract
import kotlin.test.Test

class PerfettoTraceSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = PerfettoTraceSink(FakeTracer())

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class DisabledPerfettoTraceSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = PerfettoTraceSink(FakeTracer(enabled = false))

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class FailingTracerPerfettoTraceSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = PerfettoTraceSink(FakeTracer(throwing = true))

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class SessionPerfettoTraceSinkContractTest : TraceSinkContract() {
    private val dir = java.nio.file.Files.createTempDirectory("perfetto-contract").toFile()
    private val sessions = mutableListOf<PerfettoTraceSession>()

    override fun createSink(): TraceSink =
        PerfettoTraceSession.start(dir).also { sessions += it }.sink

    override fun flush(sink: TraceSink) = sessions.forEach { it.flush() }

    @Test
    fun honoursTheSinkContract() {
        try {
            checkAll()
        } finally {
            sessions.forEach { it.close() }
            dir.deleteRecursively()
        }
    }
}
