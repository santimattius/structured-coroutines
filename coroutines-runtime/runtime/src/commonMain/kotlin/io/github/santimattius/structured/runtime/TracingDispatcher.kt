package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.TimeSource

@OptIn(ExperimentalAtomicApi::class)
private val nextDispatchId = AtomicLong(0L)

@OptIn(ExperimentalAtomicApi::class)
private fun newDispatchId(): Long = nextDispatchId.incrementAndFetch()

/**
 * A [CoroutineDispatcher] that wraps [delegate] and emits a [TraceEvent.Queued],
 * [TraceEvent.Started] and [TraceEvent.Finished] event to [sink] for every block dispatched
 * through it. [label] identifies this dispatcher in the events.
 *
 * Diagnostic tool, opt-in, not meant for production by default. When the sink is disabled for
 * [TraceLevel.Dispatch] the original block is forwarded to the delegate untouched: no wrapper and
 * no allocation. Coroutine name and thread identity are read only when the sink is enabled for
 * [TraceLevel.Coroutine]. Every `sink.emit` is guarded: a faulty sink can never break dispatch or
 * change the outcome of the block.
 *
 * Every dispatched runnable gets a process-unique `dispatchId`, shared by its queued, started and
 * finished events. When the dispatch context carries a [TraceSpan], its id, parent id and name are
 * attached to the three events; without one they are null. Reading it and allocating an id happen
 * only when the sink is enabled.
 *
 * Timestamps are nanoseconds on a monotonic base: the elapsed time since this dispatcher was
 * created. Only differences between timestamps are meaningful.
 *
 * What it observes, and what it does not. It observes dispatched segments, not all execution. A
 * segment is the run of code between two suspension points that went through [dispatch]. Not
 * visible:
 * - `Dispatchers.Unconfined`, and any delegate whose `isDispatchNeeded` is false (for example
 *   `Dispatchers.Main.immediate` when already on the main thread): the block runs inline and never
 *   reaches [dispatch].
 * - the first segment of `launch(start = CoroutineStart.UNDISPATCHED)`.
 * - `withContext` to the same dispatcher, which continues inline.
 * - the timer callback of [Delay] itself, which is not a dispatch (the resume that follows is).
 *
 * Wrapping `Dispatchers.Unconfined` is pointless, and leaves a dangling [TraceEvent.Queued] on
 * `yield()` because the delegate swallows the block. In general consumers must tolerate a
 * [TraceEvent.Queued] without matching [TraceEvent.Started] and [TraceEvent.Finished]. A coroutine
 * cancelled before its first run still emits `Started` and `Finished` for a segment that did no
 * user work.
 *
 * Wrapping also loses the identity of the delegate (the wrapper is not a
 * `MainCoroutineDispatcher`) and its elasticity and `limitedParallelism` hints, as
 * [InstrumentedDispatcher] documents. Calling `limitedParallelism` on the wrapper creates a view
 * that is not traced separately.
 *
 * It forwards [Delay]: when [delegate] has its own delay implementation (for example a
 * `TestDispatcher`, whose virtual time must be honored), timers are scheduled on it; otherwise the
 * standard default delay is used. Either way the coroutine resumes through this dispatcher, so
 * the resume is observed.
 *
 * Example:
 * ```
 * val sink = RingBufferSink(256)
 * val io = TracingDispatcher(Dispatchers.ioCompat, "io", sink)
 * withContext(io + CoroutineName("fetch")) { api.fetch() }
 * sink.snapshot().forEach(::println) // Queued, Started, Finished
 * ```
 *
 * Thread-safety: stateless apart from the immutable creation timestamp; safe to share across
 * coroutines and threads provided [sink] is thread-safe.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @constructor Wraps [delegate], emitting events to [sink] labeled [label].
 * @param delegate dispatcher that actually runs the blocks.
 * @param label name carried by every emitted [TraceEvent.dispatcher].
 * @param sink receiver of the events.
 * @see InstrumentedDispatcher
 * @see traced
 */
@OptIn(InternalCoroutinesApi::class)
@ExperimentalStructuredCoroutines
public class TracingDispatcher internal constructor(
    private val delegate: CoroutineDispatcher,
    public val label: String,
    private val sink: TraceSink,
    timeSource: TimeSource,
) : CoroutineDispatcher(),
    Delay {
    public constructor(delegate: CoroutineDispatcher, label: String, sink: TraceSink) :
        this(delegate, label, sink, TimeSource.Monotonic)

    private val originMark = timeSource.markNow()

    /** Nanoseconds elapsed since this dispatcher was created (monotonic). */
    private fun nowNanos(): Long = originMark.elapsedNow().inWholeNanoseconds

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        dispatchTraced(context, block, delegate::dispatch)
    }

    // `yield()` goes through dispatchYield, which Dispatchers.Default and Dispatchers.IO override
    // to enqueue at the tail of the queue. Forward it so wrapping does not change that fairness.
    override fun dispatchYield(
        context: CoroutineContext,
        block: Runnable,
    ) {
        dispatchTraced(context, block, delegate::dispatchYield)
    }

    private inline fun dispatchTraced(
        context: CoroutineContext,
        block: Runnable,
        forward: (CoroutineContext, Runnable) -> Unit,
    ) {
        if (!enabled(TraceLevel.Dispatch)) {
            forward(context, block)
            return
        }
        val detailed = enabled(TraceLevel.Coroutine)
        val name = if (detailed) context[CoroutineName]?.name else null
        val span = context[TraceSpan]
        val spanId = span?.id
        val parentSpanId = span?.parentId
        val spanName = span?.name
        val dispatchId = newDispatchId()
        val queuedAt = nowNanos()
        safeEmit(TraceEvent.Queued(label, queuedAt, name, spanId, parentSpanId, dispatchId, spanName))
        forward(
            context,
            Runnable {
                val startedAt = nowNanos()
                val thread = if (detailed) currentThreadInfo() else null
                safeEmit(
                    TraceEvent.Started(
                        label,
                        startedAt,
                        startedAt - queuedAt,
                        name,
                        thread,
                        spanId,
                        parentSpanId,
                        dispatchId,
                        spanName,
                    ),
                )
                var outcome: TraceOutcome = TraceOutcome.Completed
                try {
                    block.run()
                } catch (t: Throwable) {
                    outcome = TraceOutcome.Threw(t)
                    throw t
                } finally {
                    val finishedAt = nowNanos()
                    safeEmit(
                        TraceEvent.Finished(
                            label,
                            finishedAt,
                            finishedAt - startedAt,
                            outcome,
                            name,
                            thread,
                            spanId,
                            parentSpanId,
                            dispatchId,
                            spanName,
                        ),
                    )
                }
            },
        )
    }

    override fun scheduleResumeAfterDelay(
        timeMillis: Long,
        continuation: CancellableContinuation<Unit>,
    ) {
        // Resume through this dispatcher (not the delegate's own timer callback) so it is observed.
        val handle = invokeOnTimeout(timeMillis, Runnable { continuation.resume(Unit) }, continuation.context)
        continuation.invokeOnCancellation { handle.dispose() }
    }

    override fun invokeOnTimeout(
        timeMillis: Long,
        block: Runnable,
        context: CoroutineContext,
    ): DisposableHandle =
        (delegate as? Delay)?.invokeOnTimeout(timeMillis, block, context)
            ?: super<Delay>.invokeOnTimeout(timeMillis, block, context)

    private fun enabled(level: TraceLevel): Boolean =
        try {
            sink.isEnabled(level)
        } catch (_: Exception) {
            false
        }

    private fun safeEmit(event: TraceEvent) {
        try {
            sink.emit(event)
        } catch (_: Exception) {
            // A faulty sink must never break dispatch or change the block's outcome.
        }
    }
}

/**
 * A [DispatcherProvider] whose `main`, `io` and `default` dispatchers are [TracingDispatcher]s
 * reporting to [sink], created by [traced]. `unconfined` is returned unwrapped: tracing it is
 * pointless (it never queues) and would leave dangling [TraceEvent.Queued] events on `yield()`.
 *
 * Wrapping is lazy: `Dispatchers.Main` is not touched until `main` is read.
 *
 * Example:
 * ```
 * val dispatchers = DefaultDispatcherProvider.traced(RingBufferSink(256))
 * withContext(dispatchers.io) { api.fetch() }
 * dispatchers.sink // the sink that received the events
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 * Not constructed directly; use [traced].
 *
 * @see TracingDispatcher
 */
@ExperimentalStructuredCoroutines
public class TracedDispatcherProvider internal constructor(
    internal val base: DispatcherProvider,
    /** The sink that receives the events of the wrapped dispatchers. */
    public val sink: TraceSink,
) : DispatcherProvider {
    // Lazy so that `base.main` is never read (and Dispatchers.Main never touched) until used.
    private val mainLazy = lazy { TracingDispatcher(base.main, "main", sink) }
    private val ioLazy = lazy { TracingDispatcher(base.io, "io", sink) }
    private val defaultLazy = lazy { TracingDispatcher(base.default, "default", sink) }

    override val main: CoroutineDispatcher get() = mainLazy.value
    override val io: CoroutineDispatcher get() = ioLazy.value
    override val default: CoroutineDispatcher get() = defaultLazy.value
    override val unconfined: CoroutineDispatcher get() = base.unconfined
}

/**
 * Wraps this provider's `main`, `io` and `default` dispatchers in [TracingDispatcher]s labeled
 * `"main"`, `"io"` and `"default"` that report to [sink]. `unconfined` is returned as is.
 *
 * Diagnostic tool, opt-in. See [TracingDispatcher] for what is and is not observed.
 *
 * Example:
 * ```
 * val dispatchers = DefaultDispatcherProvider.traced(RingBufferSink(256))
 * val repo = UserRepository(dispatchers)
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @receiver the provider whose dispatchers are wrapped.
 * @param sink receiver of the trace events.
 * @return a provider that traces `main`, `io` and `default`.
 * @see instrumented
 */
@ExperimentalStructuredCoroutines
public fun DispatcherProvider.traced(sink: TraceSink): TracedDispatcherProvider = TracedDispatcherProvider(this, sink)

/**
 * The maximum concurrency of the traced dispatcher [label], or null when unknown. Meant to feed
 * [PoolSampler]'s `parallelism` argument.
 *
 * Returns a value only for `"default"` and `"io"`, and only when the wrapped base provider is
 * [DefaultDispatcherProvider]; for any other label (`"main"`) or base (a test provider, a custom
 * one) it returns null. On targets with no verified source (Native, JS, Wasm) it returns null too.
 *
 * The values are INFERRED from documented defaults, not read from the live scheduler: on the JVM
 * and Android `default` is `max(2, availableProcessors)` and `io` is the
 * `kotlinx.coroutines.io.parallelism` system property when it is a positive integer, else
 * `max(64, availableProcessors)`. They can be wrong if the scheduler is configured another way, and
 * for `limitedParallelism` views. `Dispatchers.Default` and `Dispatchers.IO` share one thread pool
 * with independent permits, so this is a per-dispatcher limit on concurrent segments, not a
 * thread count.
 *
 * Example:
 * ```
 * val stats = StatsSink(TraceLevel.Pool)
 * val traced = DefaultDispatcherProvider.traced(stats)
 * val sampler = PoolSampler(stats, sink, traced::parallelism)
 * ```
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @receiver the traced provider.
 * @param label dispatcher label, `"default"` or `"io"`.
 * @return the inferred maximum concurrency, or null when unknown.
 */
@ExperimentalStructuredCoroutines
public fun TracedDispatcherProvider.parallelism(label: String): Int? {
    if (base !== DefaultDispatcherProvider) return null
    return when (label) {
        "default" -> inferredDefaultParallelism()
        "io" -> inferredIoParallelism()
        else -> null
    }
}
