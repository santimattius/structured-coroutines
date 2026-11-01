@file:OptIn(ExperimentalStructuredCoroutines::class)

package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Meta-tests: the contract must pass a well-behaved sink and fail a faulty one. */
class TraceSinkContractTest {

    private class Harness(private val sink: TraceSink) : TraceSinkContract() {
        override fun createSink(): TraceSink = sink
    }

    @Test
    fun passesForAWellBehavedSink() {
        val contract = Harness(RecordingSink())
        contract.checkEmitNeverThrowsForKnownEvents()
        contract.checkEmitToleratesAbsentOptionalFields()
        contract.checkIsEnabledAnswersForEveryLevel()
        contract.checkIsEnabledIsStable()
    }

    @Test
    fun checkAllPassesForAWellBehavedSink() = Harness(RecordingSink()).checkAll()

    @Test
    fun failsWhenEmitThrows() {
        val contract = Harness(TraceSink { throw IllegalStateException("boom") })
        assertFailsWith<AssertionError> { contract.checkEmitNeverThrowsForKnownEvents() }
    }

    @Test
    fun failsWhenEmitThrowsOnlyForAbsentOptionalFields() {
        val contract = Harness(TraceSink { event ->
            if (event is TraceEvent.Started && event.thread == null) error("needs a thread")
        })
        assertFailsWith<AssertionError> { contract.checkEmitToleratesAbsentOptionalFields() }
    }

    @Test
    fun failsWhenIsEnabledChangesWithoutReconfiguration() {
        var calls = 0
        val flapping = object : TraceSink {
            override fun emit(event: TraceEvent) {}
            override fun isEnabled(level: TraceLevel): Boolean = calls++ % 2 == 0
        }
        assertFailsWith<AssertionError> { Harness(flapping).checkIsEnabledIsStable() }
    }

    @Test
    fun invokesTheFlushHookAfterEmitting() {
        var flushed = 0
        val contract = object : TraceSinkContract() {
            override fun createSink(): TraceSink = RecordingSink()
            override fun flush(sink: TraceSink) {
                flushed++
            }
        }
        contract.checkEmitNeverThrowsForKnownEvents()
        assertEquals(1, flushed)
    }
}
