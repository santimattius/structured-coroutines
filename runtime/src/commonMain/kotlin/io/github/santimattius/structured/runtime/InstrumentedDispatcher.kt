package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.TimeSource

/**
 * Immutable snapshot of what an [InstrumentedDispatcher] observed.
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
 * Diagnostic tool, opt-in, not meant for production. Wrapping a dispatcher such as
 * `Dispatchers.IO` hides its `limitedParallelism` and elasticity hints from the coroutines
 * machinery, so behavior can differ slightly from the unwrapped dispatcher.
 *
 * It also forwards [Delay]: when [delegate] has its own delay implementation (for example a
 * `TestDispatcher`, whose virtual time `delay` and `withTimeout` must honor), timers are scheduled
 * on it. Otherwise the standard default delay is used. Either way the coroutine resumes through
 * this dispatcher, so the resume is counted.
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
 */
@OptIn(ExperimentalAtomicApi::class, InternalCoroutinesApi::class)
@ExperimentalStructuredCoroutines
public class InstrumentedDispatcher internal constructor(
    private val delegate: CoroutineDispatcher,
    public val label: String,
    private val timeSource: TimeSource,
) : CoroutineDispatcher(), Delay {

    public constructor(delegate: CoroutineDispatcher, label: String) :
        this(delegate, label, TimeSource.Monotonic)

    private val total = AtomicLong(0L)
    private val running = AtomicInt(0)
    private val peak = AtomicInt(0)
    private val maxDelayNanos = AtomicLong(0L)

    override fun isDispatchNeeded(context: CoroutineContext): Boolean =
        delegate.isDispatchNeeded(context)

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        total.addAndFetch(1L)
        val queuedAt = timeSource.markNow()
        delegate.dispatch(context, Runnable {
            recordMax(maxDelayNanos, queuedAt.elapsedNow().inWholeNanoseconds)
            recordMax(peak, running.addAndFetch(1))
            try {
                block.run()
            } finally {
                running.addAndFetch(-1)
            }
        })
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        // Resume through this dispatcher (not the delegate's own timer callback) so it is counted.
        val handle = invokeOnTimeout(timeMillis, Runnable { continuation.resume(Unit) }, continuation.context)
        continuation.invokeOnCancellation { handle.dispose() }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
        (delegate as? Delay)?.invokeOnTimeout(timeMillis, block, context)
            ?: super<Delay>.invokeOnTimeout(timeMillis, block, context)

    /** Returns the stats observed so far. */
    public fun snapshot(): DispatcherStats = DispatcherStats(
        label = label,
        totalDispatched = total.load(),
        peakConcurrency = peak.load(),
        maxQueueDelay = maxDelayNanos.load().nanoseconds,
    )

    /** Zeroes the counters. Blocks currently running keep being tracked correctly. */
    public fun reset() {
        total.store(0L)
        peak.store(0)
        maxDelayNanos.store(0L)
    }

    private fun recordMax(target: AtomicLong, value: Long) {
        while (true) {
            val current = target.load()
            if (value <= current || target.compareAndSet(current, value)) return
        }
    }

    private fun recordMax(target: AtomicInt, value: Int) {
        while (true) {
            val current = target.load()
            if (value <= current || target.compareAndSet(current, value)) return
        }
    }
}

/**
 * A [DispatcherProvider] whose dispatchers are [InstrumentedDispatcher]s, created by
 * [instrumented].
 *
 * Diagnostic tool, opt-in, not meant for production. Wrapping `Dispatchers.IO` loses its
 * elasticity and `limitedParallelism` hints.
 *
 * Rules: DISPATCH_005. See `docs/BEST_PRACTICES_COROUTINES.md`.
 */
@ExperimentalStructuredCoroutines
public class InstrumentedDispatcherProvider internal constructor(
    private val base: DispatcherProvider,
) : DispatcherProvider {

    // Lazy so that `base.main` is never read (and Dispatchers.Main never touched) until used.
    private val mainLazy = lazy { InstrumentedDispatcher(base.main, "main") }
    private val ioLazy = lazy { InstrumentedDispatcher(base.io, "io") }
    private val defaultLazy = lazy { InstrumentedDispatcher(base.default, "default") }
    private val unconfinedLazy = lazy { InstrumentedDispatcher(base.unconfined, "unconfined") }

    override val main: CoroutineDispatcher get() = mainLazy.value
    override val io: CoroutineDispatcher get() = ioLazy.value
    override val default: CoroutineDispatcher get() = defaultLazy.value
    override val unconfined: CoroutineDispatcher get() = unconfinedLazy.value

    private fun materialized(): List<InstrumentedDispatcher> =
        listOf(mainLazy, ioLazy, defaultLazy, unconfinedLazy).filter { it.isInitialized() }.map { it.value }

    /** Stats of the dispatchers that have been read at least once. */
    public fun stats(): List<DispatcherStats> = materialized().map { it.snapshot() }

    /** Resets the counters of the dispatchers that have been read at least once. */
    public fun reset(): Unit = materialized().forEach { it.reset() }
}

/**
 * Wraps this provider's dispatchers in [InstrumentedDispatcher]s labeled `main`, `io`,
 * `default` and `unconfined`. Wrapping is lazy: nothing (including `Dispatchers.Main`) is read
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
 */
@ExperimentalStructuredCoroutines
public fun DispatcherProvider.instrumented(): InstrumentedDispatcherProvider =
    InstrumentedDispatcherProvider(this)
