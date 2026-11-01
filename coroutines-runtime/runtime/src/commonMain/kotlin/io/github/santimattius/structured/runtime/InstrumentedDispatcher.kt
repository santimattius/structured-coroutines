package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Immutable snapshot of what an [InstrumentedDispatcher] observed.
 *
 * Produced by [InstrumentedDispatcher.snapshot] and [InstrumentedDispatcherProvider.stats].
 *
 * @property label name given to the instrumented dispatcher.
 * @property totalDispatched number of blocks dispatched since creation or the last reset.
 * @property peakConcurrency highest number of blocks that were running at the same time.
 * @property maxQueueDelay longest wait between dispatch and the start of a block.
 */
@ExperimentalStructuredCoroutines
public data class DispatcherStats(
    val label: String,
    val totalDispatched: Long,
    val peakConcurrency: Int,
    val maxQueueDelay: Duration,
)

/**
 * A [CoroutineDispatcher] that wraps [delegate] and records how many blocks were dispatched,
 * how many ran concurrently at peak, and the longest queue delay (dispatch to block start).
 * Useful to detect saturated dispatchers while diagnosing slow or starved coroutines.
 *
 * It is a convenience over [TracingDispatcher] plus [StatsSink]: it owns one of each and reports
 * [DispatcherStats], a three-number view of [DispatcherMetrics]. For richer data (latency
 * histograms and percentiles, pending and failure counts, signals, thread information) build a
 * [TracingDispatcher] with a [StatsSink] directly.
 *
 * Diagnostic tool, opt-in, not meant for production. Wrapping a dispatcher such as
 * `Dispatchers.IO` hides its `limitedParallelism` and elasticity hints from the coroutines
 * machinery, so behavior can differ slightly from the unwrapped dispatcher. It also costs a few
 * tens of nanoseconds and some allocation per dispatch (rough, unbenchmarked measurement on JVM).
 *
 * It also forwards [Delay]: when [delegate] has its own delay implementation (for example a
 * `TestDispatcher`, whose virtual time `delay` and `withTimeout` must honor), timers are scheduled
 * on it. Otherwise the standard default delay is used. Either way the coroutine resumes through
 * this dispatcher, so the resume is counted exactly once.
 *
 * Counters are thread-safe and lock-free; read them with [snapshot] and zero them with [reset].
 *
 * Example:
 * ```
 * val io = InstrumentedDispatcher(Dispatchers.ioCompat, "io")
 * withContext(io) { api.fetch() }
 * println(io.snapshot()) // DispatcherStats(label=io, totalDispatched=1, ...)
 * ```
 *
 * Rules: DISPATCH_005 (dispatchers are injected, so they can be wrapped).
 * See `docs/BEST_PRACTICES_COROUTINES.md` section 3.5.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @constructor Wraps [delegate] and labels it [label] in the recorded stats.
 * @param delegate the dispatcher that actually runs the blocks.
 * @param label name reported in [DispatcherStats.label].
 * @see TracingDispatcher
 * @see instrumented
 */
@OptIn(InternalCoroutinesApi::class)
@ExperimentalStructuredCoroutines
public class InstrumentedDispatcher internal constructor(
    delegate: CoroutineDispatcher,
    public val label: String,
    timeSource: TimeSource,
) : CoroutineDispatcher(), Delay {

    public constructor(delegate: CoroutineDispatcher, label: String) :
        this(delegate, label, TimeSource.Monotonic)

    private val stats = StatsSink(maxLevel = TraceLevel.Dispatch)
    private val tracing = TracingDispatcher(delegate, label, stats, timeSource)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        tracing.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tracing.dispatch(context, block)
    }

    override fun dispatchYield(context: CoroutineContext, block: Runnable) {
        tracing.dispatchYield(context, block)
    }

    // The continuation's interceptor is this (outer) dispatcher, so the resume that `tracing`
    // performs goes outer.dispatch -> tracing.dispatch and is counted exactly once.
    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        tracing.scheduleResumeAfterDelay(timeMillis, continuation)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
        tracing.invokeOnTimeout(timeMillis, block, context)

    /**
     * Returns the stats observed so far. Cheap and safe to call from any thread; the returned
     * [DispatcherStats] is an immutable copy and is not updated afterwards.
     */
    public fun snapshot(): DispatcherStats {
        val m = stats.metrics(label)
        return DispatcherStats(
            label = label,
            totalDispatched = m?.queued ?: 0L,
            peakConcurrency = m?.peakRunning ?: 0,
            maxQueueDelay = m?.queueDelay?.max ?: Duration.ZERO,
        )
    }

    /**
     * Zeroes the counters. Blocks that are running while this is called finish without being
     * counted again, but they are no longer part of the concurrency level seen by later blocks,
     * so [DispatcherStats.peakConcurrency] right after a reset under load can be lower than the
     * true overlap with them.
     */
    public fun reset() {
        stats.reset()
    }
}

/**
 * A [DispatcherProvider] whose `main`, `io` and `default` dispatchers are [InstrumentedDispatcher]s,
 * created by [instrumented]. `unconfined` is returned unwrapped: it never queues work, so its
 * counters would always read zero, and `yield()` on a wrapped one would count a dispatch that never
 * starts.
 *
 * Diagnostic tool, opt-in, not meant for production. Wrapping `Dispatchers.IO` loses its
 * elasticity and `limitedParallelism` hints.
 *
 * Rules: DISPATCH_005. See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 * Not constructed directly; use [instrumented].
 */
@ExperimentalStructuredCoroutines
public class InstrumentedDispatcherProvider internal constructor(
    private val base: DispatcherProvider,
) : DispatcherProvider {

    // Lazy so that `base.main` is never read (and Dispatchers.Main never touched) until used.
    private val mainLazy = lazy { InstrumentedDispatcher(base.main, "main") }
    private val ioLazy = lazy { InstrumentedDispatcher(base.io, "io") }
    private val defaultLazy = lazy { InstrumentedDispatcher(base.default, "default") }

    override val main: CoroutineDispatcher get() = mainLazy.value
    override val io: CoroutineDispatcher get() = ioLazy.value
    override val default: CoroutineDispatcher get() = defaultLazy.value
    override val unconfined: CoroutineDispatcher get() = base.unconfined

    private fun materialized(): List<InstrumentedDispatcher> =
        listOf(mainLazy, ioLazy, defaultLazy).filter { it.isInitialized() }.map { it.value }

    /**
     * Stats of the dispatchers that have been read at least once, in `main`, `io`, `default`
     * order. Dispatchers never accessed are omitted.
     */
    public fun stats(): List<DispatcherStats> = materialized().map { it.snapshot() }

    /** Resets the counters of the dispatchers that have been read at least once. */
    public fun reset(): Unit = materialized().forEach { it.reset() }
}

/**
 * Wraps this provider's `main`, `io` and `default` dispatchers in [InstrumentedDispatcher]s with
 * those labels; `unconfined` is returned as is (see [InstrumentedDispatcherProvider]). Wrapping is lazy: nothing (including `Dispatchers.Main`) is read
 * until the corresponding property is accessed.
 *
 * Diagnostic tool, opt-in, not meant for production. Wrapping `Dispatchers.IO` loses its
 * elasticity and `limitedParallelism` hints.
 *
 * Example:
 * ```
 * val dispatchers = DefaultDispatcherProvider.instrumented()
 * val repo = UserRepository(dispatchers)
 * repo.load()
 * dispatchers.stats().forEach(::println)
 * ```
 *
 * Rules: DISPATCH_005. See `docs/BEST_PRACTICES_COROUTINES.md` section 3.5.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @receiver the provider whose dispatchers are wrapped.
 * @return a provider whose `main`, `io` and `default` dispatchers are [InstrumentedDispatcher]s.
 * @see traced
 */
@ExperimentalStructuredCoroutines
public fun DispatcherProvider.instrumented(): InstrumentedDispatcherProvider =
    InstrumentedDispatcherProvider(this)
