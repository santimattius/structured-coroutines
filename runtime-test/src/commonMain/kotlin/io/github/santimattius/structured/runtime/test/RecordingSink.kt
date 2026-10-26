package io.github.santimattius.structured.runtime.test

import io.github.santimattius.structured.runtime.ExperimentalStructuredCoroutines
import io.github.santimattius.structured.runtime.TraceEvent
import io.github.santimattius.structured.runtime.TraceLevel
import io.github.santimattius.structured.runtime.TraceSink
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * [TraceSink] for tests: records every event handed to [emit], in order, and exposes them for
 * assertions.
 *
 * Safe for concurrent [emit]. Implementation: one [AtomicReference] holding an immutable list,
 * replaced by compare-and-set (the common stdlib has no lock). Each emit copies the list, which is
 * fine for test-sized recordings but not meant for long-running production use; use `RingBufferSink`
 * there.
 *
 * [emit] records unconditionally, whatever [isEnabled] says: filtering by level is the caller's job
 * (as in `CompositeSink` or `TracingDispatcher`), so a test can assert that a caller did or did not
 * emit. Set [maxLevel] to exercise the disabled fast path of the code under test.
 *
 * Example:
 * ```
 * val sink = RecordingSink()
 * val dispatchers = testDispatcherProvider().traced(sink)
 * // ... run traced work ...
 * assertEquals(1, sink.eventsOf<TraceEvent.Finished>().size)
 * ```
 *
 * @param maxLevel highest [TraceLevel] reported as enabled by [isEnabled]; levels are cumulative.
 *   Defaults to [TraceLevel.Pool] (every level enabled); `null` disables every level.
 */
@OptIn(ExperimentalAtomicApi::class)
@ExperimentalStructuredCoroutines
public class RecordingSink(
    public val maxLevel: TraceLevel? = TraceLevel.Pool,
) : TraceSink {

    private val recorded = AtomicReference<List<TraceEvent>>(emptyList())

    /** A snapshot of the recorded events, oldest first. Later emits do not change it. */
    public val events: List<TraceEvent>
        get() = recorded.load()

    /** The recorded events that are instances of [T], oldest first. */
    public inline fun <reified T : TraceEvent> eventsOf(): List<T> = events.filterIsInstance<T>()

    /** Drops every recorded event. */
    public fun clear() {
        recorded.store(emptyList())
    }

    override fun isEnabled(level: TraceLevel): Boolean = maxLevel != null && level <= maxLevel

    override fun emit(event: TraceEvent) {
        while (true) {
            val current = recorded.load()
            if (recorded.compareAndSet(current, current + event)) return
        }
    }
}
