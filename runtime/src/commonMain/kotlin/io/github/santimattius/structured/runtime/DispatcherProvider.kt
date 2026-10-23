package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Abstraction over the four standard dispatchers, meant to be injected into classes instead of
 * hardcoding `Dispatchers.*` so tests can swap in a test dispatcher with virtual time.
 *
 * Example:
 * ```
 * class UserRepository(private val dispatchers: DispatcherProvider = DefaultDispatcherProvider) {
 *     suspend fun load() = withContext(dispatchers.io) { api.fetch() }
 * }
 *
 * // In a test:
 * val repo = UserRepository(DispatcherProvider.of(StandardTestDispatcher(testScheduler)))
 * ```
 *
 * Rules: DISPATCH_005 (inject dispatchers for testability), KMP_001 (no `Dispatchers.IO` in
 * commonMain; see [ioCompat]), TEST_005 (no hardcoded dispatcher in a class).
 * See `docs/BEST_PRACTICES_COROUTINES.md` sections 3.5, 11.1 and 6.5.
 */
@ExperimentalStructuredCoroutines
public interface DispatcherProvider {
    /** UI dispatcher. May be unavailable on platforms without a main dispatcher. */
    public val main: CoroutineDispatcher

    /** Dispatcher for blocking IO. */
    public val io: CoroutineDispatcher

    /** Dispatcher for CPU-bound work. */
    public val default: CoroutineDispatcher

    /** Unconfined dispatcher. */
    public val unconfined: CoroutineDispatcher

    public companion object {
        /**
         * Returns a provider whose four dispatchers are all [dispatcher]. Intended for tests:
         * `DispatcherProvider.of(StandardTestDispatcher(testScheduler))`.
         *
         * Rules: DISPATCH_005, TEST_005. See `docs/BEST_PRACTICES_COROUTINES.md`.
         */
        public fun of(dispatcher: CoroutineDispatcher): DispatcherProvider = SingleDispatcherProvider(dispatcher)
    }
}

private class SingleDispatcherProvider(dispatcher: CoroutineDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val unconfined: CoroutineDispatcher = dispatcher
}

/**
 * Production [DispatcherProvider] backed by the real [Dispatchers].
 *
 * [main] is resolved on every access and never at construction, so using this provider on
 * platforms without a main dispatcher (plain JVM) only fails if `main` is actually read.
 * [io] uses [ioCompat], which is safe in common code.
 *
 * Example:
 * ```
 * val repo = UserRepository(DefaultDispatcherProvider)
 * ```
 *
 * Rules: DISPATCH_005, KMP_001, TEST_005. See `docs/BEST_PRACTICES_COROUTINES.md`.
 */
@ExperimentalStructuredCoroutines
public object DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.ioCompat
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
}
