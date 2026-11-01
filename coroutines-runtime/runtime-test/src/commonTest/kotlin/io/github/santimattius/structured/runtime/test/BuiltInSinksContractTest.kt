@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.CompositeSink
import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.LoggingSink
import io.github.santimattius.structured.runtime.RingBufferSink
import io.github.santimattius.structured.runtime.StatsSink
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSink
import kotlin.test.Test

// Dogfooding: every built-in sink must pass the same contract a third-party sink would run.

class RingBufferSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = RingBufferSink(capacity = 64)

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class LoggingSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = LoggingSink(TraceLevel.Pool) { }

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class StatsSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = StatsSink(TraceLevel.Pool)

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class CompositeSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink =
        CompositeSink(RingBufferSink(capacity = 64), StatsSink(TraceLevel.Pool), RecordingSink())

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class RecordingSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = RecordingSink()

    @Test
    fun honoursTheSinkContract() = checkAll()
}

class DisabledRecordingSinkContractTest : TraceSinkContract() {
    override fun createSink(): TraceSink = RecordingSink(maxLevel = null)

    @Test
    fun honoursTheSinkContract() = checkAll()
}
