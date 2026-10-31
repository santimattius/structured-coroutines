package io.github.santimattius.structured.runtime

/**
 * Port that receives [TraceEvent]s. Backends (logging, ring buffers, platform tracers) implement it.
 *
 * Contract every sink must follow:
 * 1. [emit] must not throw; callers also catch and drop, so a faulty sink never breaks dispatch.
 * 2. [emit] must be cheap and must not suspend or block; heavy work goes behind the sink's own buffer.
 * 3. [emit] can be called concurrently from many threads.
 * 4. Ordering is guaranteed per thread only, not globally. Correlate the three lifecycle events of
 *    one dispatched runnable by their non-zero `dispatchId`, not by adjacency or span id.
 * 5. A sink may ignore unknown event subtypes (new ones can be added in later releases).
 *
 * [isEnabled] is consulted before an event is built, so a disabled level costs no allocation.
 *
 * Example:
 * ```
 * val sink = TraceSink { event -> println(event) }
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @see CompositeSink
 * @see RingBufferSink
 * @see StatsSink
 * @see LoggingSink
 */
@ExperimentalStructuredCoroutines
public fun interface TraceSink {
    /**
     * Receives one [event]. See the contract in the type documentation.
     *
     * @param event the observation to record.
     */
    public fun emit(event: TraceEvent)

    /**
     * Whether this sink wants events of [level]. Defaults to true for every level.
     *
     * @param level the detail level about to be produced.
     */
    public fun isEnabled(level: TraceLevel): Boolean = true

    /** Constants for [TraceSink]. */
    public companion object {
        /** A sink that is disabled for every level and ignores every event. */
        public val None: TraceSink =
            object : TraceSink {
                override fun emit(event: TraceEvent) {}

                override fun isEnabled(level: TraceLevel): Boolean = false

                override fun toString(): String = "TraceSink.None"
            }
    }
}

/**
 * Fans every event out to several sinks. Enabled for a level when any sink is. Each sink is
 * called in isolation: an [Exception] from one does not stop the others (VM errors propagate).
 * Only sinks enabled for the event's level receive it.
 *
 * Example:
 * ```
 * val sink = CompositeSink(RingBufferSink(256), LoggingSink(log = ::println))
 * ```
 *
 * The list is copied at construction, so later changes to it have no effect. Events are delivered to
 * the sinks in list order on the emitting thread.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param sinks the sinks to fan out to.
 */
@ExperimentalStructuredCoroutines
public class CompositeSink(
    sinks: List<TraceSink>,
) : TraceSink {
    private val sinks: List<TraceSink> = sinks.toList()

    override fun emit(event: TraceEvent) {
        val level = event.level
        for (sink in sinks) {
            try {
                if (sink.isEnabled(level)) sink.emit(event)
            } catch (_: Exception) {
                // Isolated on purpose: one faulty sink must not starve the rest.
            }
        }
    }

    override fun isEnabled(level: TraceLevel): Boolean =
        sinks.any {
            try {
                it.isEnabled(level)
            } catch (_: Exception) {
                false
            }
        }
}

/**
 * Creates a [CompositeSink] over [sinks].
 *
 * @param sinks the sinks to fan out to.
 */
@ExperimentalStructuredCoroutines
public fun CompositeSink(vararg sinks: TraceSink): CompositeSink = CompositeSink(sinks.toList())
