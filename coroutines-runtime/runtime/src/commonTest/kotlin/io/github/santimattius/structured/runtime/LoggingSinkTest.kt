package io.github.santimattius.structured.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalStructuredCoroutines::class)
class LoggingSinkTest {

    @Test
    fun logsOneLinePerEventUsingToString() {
        val lines = mutableListOf<String>()
        val sink = LoggingSink(log = { lines += it })
        val e = TraceEvent.Queued("io", 1L, "job")
        sink.emit(e)
        assertEquals(listOf(e.toString()), lines)
    }

    @Test
    fun enabledUpToMaxLevel() {
        val sink = LoggingSink(log = {})
        assertTrue(sink.isEnabled(TraceLevel.Dispatch))
        assertFalse(sink.isEnabled(TraceLevel.Coroutine))
        assertTrue(LoggingSink(TraceLevel.Pool, {}).isEnabled(TraceLevel.Pool))
    }

    @Test
    fun throwingLoggerIsTolerated() {
        val sink = LoggingSink(log = { throw IllegalStateException("boom") })
        sink.emit(TraceEvent.Queued("io", 1L, null))
    }
}
