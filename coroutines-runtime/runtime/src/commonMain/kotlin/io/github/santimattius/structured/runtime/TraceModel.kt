package io.github.santimattius.structured.runtime

import kotlin.jvm.JvmOverloads

/**
 * How much detail a [TraceSink] wants to receive. Levels are cumulative by ordinal: enabling a
 * level also enables every level below it, so `level <= maxLevel` is the enablement test.
 *
 * - [Dispatch]: queued, started and finished events per dispatch. The cheapest useful level.
 * - [Coroutine]: additionally populates the coroutine name and the thread identity on run events
 *   (where the platform supports it).
 * - [Pool]: additionally enables periodic [TraceEvent.PoolSample]s, produced by [PoolSampler].
 *
 * New levels can be appended in the future without breaking existing sinks.
 *
 * Example:
 * ```
 * val enabled = TraceLevel.Dispatch <= TraceLevel.Coroutine // true
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 */
@ExperimentalStructuredCoroutines
public enum class TraceLevel {
    /** Queued, started and finished events. */
    Dispatch,

    /** Adds coroutine name and thread identity on run events. */
    Coroutine,

    /** Adds periodic pool samples ([TraceEvent.PoolSample]). */
    Pool,
}

/**
 * Identity of a platform thread. A plain final class (not a data class) so fields can be added
 * later without a binary-incompatible `copy`/`componentN` change.
 *
 * Example:
 * ```
 * val thread = ThreadInfo(id = 42L, name = "DefaultDispatcher-worker-1")
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines]. Only populated on targets with threads
 * (JVM and Android); see [platformCapabilities].
 *
 * @property id platform thread id.
 * @property name platform thread name.
 */
@ExperimentalStructuredCoroutines
public class ThreadInfo(
    public val id: Long,
    public val name: String,
) {
    override fun equals(other: Any?): Boolean = this === other || (other is ThreadInfo && id == other.id && name == other.name)

    override fun hashCode(): Int = 31 * id.hashCode() + name.hashCode()

    override fun toString(): String = "ThreadInfo(id=$id, name=$name)"
}

/**
 * How a traced block ended.
 *
 * Example:
 * ```
 * when (finished.outcome) {
 *     TraceOutcome.Completed -> ok()
 *     is TraceOutcome.Threw -> report(finished.outcome.error)
 * }
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 */
@ExperimentalStructuredCoroutines
public sealed interface TraceOutcome {
    /** The block returned normally. */
    public object Completed : TraceOutcome {
        override fun toString(): String = "Completed"
    }

    /**
     * The block ended by throwing [error] (including cancellation).
     *
     * @property error the throwable that ended the block.
     */
    public class Threw(
        public val error: Throwable,
    ) : TraceOutcome {
        override fun equals(other: Any?): Boolean = this === other || (other is Threw && error == other.error)

        override fun hashCode(): Int = error.hashCode()

        override fun toString(): String = "Threw(error=$error)"
    }
}

/**
 * An observation emitted to a [TraceSink]. Sealed so subtypes can be added later; sinks must
 * ignore subtypes they do not know.
 *
 * Subclasses are final classes with explicit `equals`/`hashCode`/`toString` rather than data
 * classes, so new fields can be added in later releases without breaking binary compatibility.
 * `coroutineName` and `thread` are null unless [TraceLevel.Coroutine] is enabled and the platform
 * supports it.
 *
 * Example:
 * ```
 * val sink = TraceSink { event ->
 *     if (event is TraceEvent.Finished) println("${event.dispatcher} ran ${event.runTimeNanos}ns")
 * }
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @see TraceSink
 */
@ExperimentalStructuredCoroutines
public sealed interface TraceEvent {
    /** Label of the dispatcher that produced the event, such as "io" or "default". */
    public val dispatcher: String

    /** Monotonic timestamp in nanoseconds. Only differences between events are meaningful. */
    public val timestampNanos: Long

    /** Lowest [TraceLevel] at which this event is produced. */
    public val level: TraceLevel

    /**
     * A block was handed to the dispatcher. [dispatchId] identifies this one dispatched runnable
     * across its [Queued], [Started] and [Finished] events. [spanId], [parentSpanId] and [spanName]
     * come from the [TraceSpan] in the dispatch context and are null when there is none (same on
     * [Started] and [Finished]).
     *
     * @property coroutineName name from the `CoroutineName` element; null unless [TraceLevel.Coroutine] is enabled.
     * @property spanId id of the enclosing [TraceSpan], or null.
     * @property parentSpanId id of that span's parent, or null.
     * @property dispatchId process-unique id of this dispatched runnable. Zero is reserved for manually constructed events.
     * @property spanName low-cardinality name of the enclosing [TraceSpan], or null.
     */
    public class Queued
        @JvmOverloads
        constructor(
            override val dispatcher: String,
            override val timestampNanos: Long,
            public val coroutineName: String?,
            public val spanId: Long? = null,
            public val parentSpanId: Long? = null,
            public val dispatchId: Long = 0L,
            public val spanName: String? = null,
        ) : TraceEvent {
            override val level: TraceLevel get() = TraceLevel.Dispatch

            override fun equals(other: Any?): Boolean =
                this === other || (
                    other is Queued && dispatcher == other.dispatcher &&
                        timestampNanos == other.timestampNanos && coroutineName == other.coroutineName &&
                        spanId == other.spanId && parentSpanId == other.parentSpanId &&
                        dispatchId == other.dispatchId && spanName == other.spanName
                )

            override fun hashCode(): Int {
                var h = dispatcher.hashCode()
                h = 31 * h + timestampNanos.hashCode()
                h = 31 * h + (coroutineName?.hashCode() ?: 0)
                h = 31 * h + (spanId?.hashCode() ?: 0)
                h = 31 * h + (parentSpanId?.hashCode() ?: 0)
                h = 31 * h + dispatchId.hashCode()
                h = 31 * h + (spanName?.hashCode() ?: 0)
                return h
            }

            override fun toString(): String =
                "Queued(dispatcher=$dispatcher, timestampNanos=$timestampNanos, coroutineName=$coroutineName, " +
                    "spanId=$spanId, parentSpanId=$parentSpanId, dispatchId=$dispatchId, spanName=$spanName)"
        }

    /**
     * A block began running after waiting [queueDelayNanos] in the queue.
     *
     * @property queueDelayNanos time between dispatch and start, in nanoseconds.
     * @property coroutineName see [Queued.coroutineName].
     * @property thread thread that runs the block; null unless [TraceLevel.Coroutine] is enabled and the platform has threads.
     * @property spanId see [Queued.spanId].
     * @property parentSpanId see [Queued.parentSpanId].
     * @property dispatchId see [Queued.dispatchId].
     * @property spanName see [Queued.spanName].
     */
    public class Started
        @JvmOverloads
        constructor(
            override val dispatcher: String,
            override val timestampNanos: Long,
            public val queueDelayNanos: Long,
            public val coroutineName: String?,
            public val thread: ThreadInfo?,
            public val spanId: Long? = null,
            public val parentSpanId: Long? = null,
            public val dispatchId: Long = 0L,
            public val spanName: String? = null,
        ) : TraceEvent {
            override val level: TraceLevel get() = TraceLevel.Dispatch

            override fun equals(other: Any?): Boolean =
                this === other || (
                    other is Started && dispatcher == other.dispatcher &&
                        timestampNanos == other.timestampNanos && queueDelayNanos == other.queueDelayNanos &&
                        coroutineName == other.coroutineName && thread == other.thread &&
                        spanId == other.spanId && parentSpanId == other.parentSpanId &&
                        dispatchId == other.dispatchId && spanName == other.spanName
                )

            override fun hashCode(): Int {
                var h = dispatcher.hashCode()
                h = 31 * h + timestampNanos.hashCode()
                h = 31 * h + queueDelayNanos.hashCode()
                h = 31 * h + (coroutineName?.hashCode() ?: 0)
                h = 31 * h + (thread?.hashCode() ?: 0)
                h = 31 * h + (spanId?.hashCode() ?: 0)
                h = 31 * h + (parentSpanId?.hashCode() ?: 0)
                h = 31 * h + dispatchId.hashCode()
                h = 31 * h + (spanName?.hashCode() ?: 0)
                return h
            }

            override fun toString(): String =
                "Started(dispatcher=$dispatcher, timestampNanos=$timestampNanos, " +
                    "queueDelayNanos=$queueDelayNanos, coroutineName=$coroutineName, thread=$thread, " +
                    "spanId=$spanId, parentSpanId=$parentSpanId, dispatchId=$dispatchId, spanName=$spanName)"
        }

    /**
     * A block finished after running for [runTimeNanos], with the given [outcome].
     *
     * @property runTimeNanos time the block ran, in nanoseconds.
     * @property outcome how the block ended.
     * @property coroutineName see [Queued.coroutineName].
     * @property thread see [Started.thread].
     * @property spanId see [Queued.spanId].
     * @property parentSpanId see [Queued.parentSpanId].
     * @property dispatchId see [Queued.dispatchId].
     * @property spanName see [Queued.spanName].
     */
    public class Finished
        @JvmOverloads
        constructor(
            override val dispatcher: String,
            override val timestampNanos: Long,
            public val runTimeNanos: Long,
            public val outcome: TraceOutcome,
            public val coroutineName: String?,
            public val thread: ThreadInfo?,
            public val spanId: Long? = null,
            public val parentSpanId: Long? = null,
            public val dispatchId: Long = 0L,
            public val spanName: String? = null,
        ) : TraceEvent {
            override val level: TraceLevel get() = TraceLevel.Dispatch

            override fun equals(other: Any?): Boolean =
                this === other || (
                    other is Finished && dispatcher == other.dispatcher &&
                        timestampNanos == other.timestampNanos && runTimeNanos == other.runTimeNanos &&
                        outcome == other.outcome && coroutineName == other.coroutineName &&
                        thread == other.thread &&
                        spanId == other.spanId && parentSpanId == other.parentSpanId &&
                        dispatchId == other.dispatchId && spanName == other.spanName
                )

            override fun hashCode(): Int {
                var h = dispatcher.hashCode()
                h = 31 * h + timestampNanos.hashCode()
                h = 31 * h + runTimeNanos.hashCode()
                h = 31 * h + outcome.hashCode()
                h = 31 * h + (coroutineName?.hashCode() ?: 0)
                h = 31 * h + (thread?.hashCode() ?: 0)
                h = 31 * h + (spanId?.hashCode() ?: 0)
                h = 31 * h + (parentSpanId?.hashCode() ?: 0)
                h = 31 * h + dispatchId.hashCode()
                h = 31 * h + (spanName?.hashCode() ?: 0)
                return h
            }

            override fun toString(): String =
                "Finished(dispatcher=$dispatcher, timestampNanos=$timestampNanos, " +
                    "runTimeNanos=$runTimeNanos, outcome=$outcome, coroutineName=$coroutineName, thread=$thread, " +
                    "spanId=$spanId, parentSpanId=$parentSpanId, dispatchId=$dispatchId, spanName=$spanName)"
        }

    /**
     * A periodic snapshot of one dispatcher pool, produced by [PoolSampler] (never by
     * [TracingDispatcher]) at [TraceLevel.Pool]. A series of samples shows how the pool behaves
     * over time, which cumulative [DispatcherMetrics] cannot.
     *
     * [queued], [started] and [finished] are cumulative counters from [DispatcherMetrics]. [pending]
     * is `max(0, queued - started)` and can overcount when dangling [Queued] events exist (a
     * dispatcher that swallows blocks, see [TracingDispatcher]). [running] is `started - finished`
     * and [peakRunning] the highest concurrency observed so far. [parallelism] is the configured or
     * inferred maximum concurrency of the pool when known, else null (see
     * [TracedDispatcherProvider.parallelism]).
     *
     * [timestampNanos] is measured from the creation of the [PoolSampler] that produced the sample,
     * not from the creation of any [TracingDispatcher]: only differences between samples of the same
     * sampler are meaningful.
     *
     * Example:
     * ```
     * val sink = TraceSink { event ->
     *     if (event is TraceEvent.PoolSample) println("${event.dispatcher}: pending=${event.pending}")
     * }
     * ```
     *
     * @property queued cumulative number of queued events.
     * @property started cumulative number of started events.
     * @property finished cumulative number of finished events.
     * @property pending queued and not yet started segments.
     * @property running segments currently running.
     * @property peakRunning highest concurrency observed so far.
     * @property parallelism configured or inferred concurrency limit, or null when unknown.
     */
    public class PoolSample(
        override val dispatcher: String,
        override val timestampNanos: Long,
        public val queued: Long,
        public val started: Long,
        public val finished: Long,
        public val pending: Long,
        public val running: Int,
        public val peakRunning: Int,
        public val parallelism: Int?,
    ) : TraceEvent {
        override val level: TraceLevel get() = TraceLevel.Pool

        override fun equals(other: Any?): Boolean =
            this === other || (
                other is PoolSample && dispatcher == other.dispatcher &&
                    timestampNanos == other.timestampNanos && queued == other.queued &&
                    started == other.started && finished == other.finished && pending == other.pending &&
                    running == other.running && peakRunning == other.peakRunning &&
                    parallelism == other.parallelism
            )

        override fun hashCode(): Int {
            var h = dispatcher.hashCode()
            h = 31 * h + timestampNanos.hashCode()
            h = 31 * h + queued.hashCode()
            h = 31 * h + started.hashCode()
            h = 31 * h + finished.hashCode()
            h = 31 * h + pending.hashCode()
            h = 31 * h + running
            h = 31 * h + peakRunning
            h = 31 * h + (parallelism ?: 0)
            return h
        }

        override fun toString(): String =
            "PoolSample(dispatcher=$dispatcher, timestampNanos=$timestampNanos, queued=$queued, " +
                "started=$started, finished=$finished, pending=$pending, running=$running, " +
                "peakRunning=$peakRunning, parallelism=$parallelism)"
    }
}
