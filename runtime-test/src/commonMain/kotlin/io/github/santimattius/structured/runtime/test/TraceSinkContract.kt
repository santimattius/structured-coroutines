package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.ThreadInfo
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceOutcome
import io.github.santimattius.structured.runtime.TraceSink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Reusable contract checks for [TraceSink] implementations, built-in or third-party. Subclass it in
 * your own tests, provide [createSink] and call [checkAll] from one test. It is annotation-free on
 * purpose: `kotlin.test` annotations resolve per test framework on the JVM, so shipping them from
 * this library would force JUnit 4 on consumers. Assertions use `kotlin.test`, so it runs on every
 * target.
 *
 * It checks the rules of the [TraceSink] contract that can be observed from outside:
 * - rule 1, `emit` must not throw: for every known [TraceEvent] subtype (including a
 *   [TraceOutcome.Threw] finish) and with every optional field absent;
 * - rule 3, `emit` can be called concurrently: many coroutines on `Dispatchers.Default` emit
 *   without error (no ordering or count is asserted, rule 4 only promises per-thread order);
 * - [TraceSink.isEnabled] answers for every [TraceLevel] and does not change unless the sink is
 *   reconfigured.
 *
 * Rule 2 (cheap, non-blocking) and rule 5 (ignore unknown subtypes; [TraceEvent] is sealed, so a
 * foreign subtype cannot be built) are not testable here.
 *
 * Example:
 * ```
 * class MyBackendSinkTest : TraceSinkContract() {
 *     override fun createSink(): TraceSink = MyBackendSink(FakeBackend())
 *
 *     @Test
 *     fun honoursTheSinkContract() = checkAll()
 * }
 * ```
 *
 * Individual `check*` functions are public so they can be run one by one. [createSink] is called once per check. Sinks that buffer asynchronously can override [flush] to
 * wait for the buffer to drain, so a failure in the background worker is still seen by the test.
 */
@ExperimentalStructuredCoroutines
public abstract class TraceSinkContract {

    /** Creates a fresh sink under test. Called once per check. */
    protected abstract fun createSink(): TraceSink

    /**
     * Called after a test emitted events, to let a sink that processes asynchronously finish before
     * the test returns. The default does nothing.
     */
    protected open fun flush(sink: TraceSink) {}

    /** Runs every check below. Returns a [TestResult] so it can be the body of a test on every target. */
    public fun checkAll(): TestResult = runTest {
        checkEmitNeverThrowsForKnownEvents()
        checkEmitToleratesAbsentOptionalFields()
        checkEmitToleratesConcurrentCallers()
        checkIsEnabledAnswersForEveryLevel()
        checkIsEnabledIsStable()
    }

    /** Rule 1: `emit` does not throw for any known [TraceEvent] subtype, including a threw outcome. */
    public fun checkEmitNeverThrowsForKnownEvents() {
        val sink = createSink()
        val thread = ThreadInfo(id = 1L, name = "contract-thread")

        val events = listOf(
            TraceEvent.Queued("io", 1L, "name", spanId = 1L, parentSpanId = 0L),
            TraceEvent.Started("io", 2L, 1L, "name", thread, spanId = 1L, parentSpanId = 0L),
            TraceEvent.Finished("io", 3L, 1L, TraceOutcome.Completed, "name", thread, spanId = 1L, parentSpanId = 0L),
            TraceEvent.Finished("io", 4L, 1L, TraceOutcome.Threw(IllegalStateException("boom")), "name", thread),
            TraceEvent.PoolSample("io", 5L, queued = 3L, started = 2L, finished = 1L, pending = 1L, running = 1, peakRunning = 2, parallelism = 4),
        )
        for (event in events) assertDoesNotThrow("emit(${event::class.simpleName})") { sink.emit(event) }
        flush(sink)
    }

    /** Optional capabilities absent (no name, thread, span, parallelism) must not break the sink. */
    public fun checkEmitToleratesAbsentOptionalFields() {
        val sink = createSink()

        val events = listOf(
            TraceEvent.Queued("default", 1L, coroutineName = null),
            TraceEvent.Started("default", 2L, queueDelayNanos = 0L, coroutineName = null, thread = null),
            TraceEvent.Finished("default", 3L, runTimeNanos = 0L, outcome = TraceOutcome.Completed, coroutineName = null, thread = null),
            TraceEvent.PoolSample("default", 4L, queued = 0L, started = 0L, finished = 0L, pending = 0L, running = 0, peakRunning = 0, parallelism = null),
        )
        for (event in events) assertDoesNotThrow("emit(${event::class.simpleName}) without optional fields") { sink.emit(event) }
        flush(sink)
    }

    /** Rule 3: many coroutines on `Dispatchers.Default` emit concurrently; no ordering is asserted. */
    public suspend fun checkEmitToleratesConcurrentCallers() {
        val sink = createSink()
        val coroutines = 32
        val perCoroutine = 50

        withContext(Dispatchers.Default) {
            coroutineScope {
                repeat(coroutines) { c ->
                    launch {
                        repeat(perCoroutine) { i ->
                            val ts = (c * perCoroutine + i).toLong()
                            sink.emit(TraceEvent.Queued("default", ts, coroutineName = null))
                            sink.emit(TraceEvent.Started("default", ts, 0L, coroutineName = null, thread = null))
                            sink.emit(TraceEvent.Finished("default", ts, 0L, TraceOutcome.Completed, coroutineName = null, thread = null))
                        }
                    }
                }
            }
        }
        flush(sink)
    }

    /** [TraceSink.isEnabled] answers for every [TraceLevel] without throwing. */
    public fun checkIsEnabledAnswersForEveryLevel() {
        val sink = createSink()
        for (level in TraceLevel.entries) assertDoesNotThrow("isEnabled($level)") { sink.isEnabled(level) }
    }

    /** [TraceSink.isEnabled] is unchanged by emitting events (no reconfiguration happened). */
    public fun checkIsEnabledIsStable() {
        val sink = createSink()
        val before = TraceLevel.entries.associateWith { sink.isEnabled(it) }

        sink.emit(TraceEvent.Queued("io", 1L, coroutineName = null))
        sink.emit(TraceEvent.PoolSample("io", 2L, 0L, 0L, 0L, 0L, 0, 0, null))
        flush(sink)

        val after = TraceLevel.entries.associateWith { sink.isEnabled(it) }
        assertEquals(before, after, "isEnabled changed without reconfiguration")
        // Repeated calls agree too.
        assertEquals(before, TraceLevel.entries.associateWith { sink.isEnabled(it) })
    }

    private fun assertDoesNotThrow(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            fail("$what threw $e")
        }
    }
}
