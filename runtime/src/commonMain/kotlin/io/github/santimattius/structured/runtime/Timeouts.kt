package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

/**
 * Runs [block] with a timeout of [timeoutMillis] and reports a timeout as a failed [Result]
 * instead of throwing, so it never cancels the parent scope.
 *
 * - If this call's own timeout fires, returns `Result.failure(TimeoutCancellationException)`.
 * - If [block] completes in time, returns `Result.success`.
 * - If the caller is cancelled (including by an outer `withTimeout` firing), the
 *   [CancellationException] is rethrown, never captured.
 * - Any other exception thrown by [block], including a [TimeoutCancellationException]
 *   escaping from a nested `withTimeout`, propagates unchanged.
 *
 * Example:
 * ```
 * val profile = withTimeoutResult(2_000) { api.loadProfile() }
 *     .getOrElse { cachedProfile }
 * ```
 *
 * Rules: CANCEL_006 (handle `TimeoutCancellationException` so it does not cancel the scope),
 * CANCEL_007 (clean up resources in `finally`/`NonCancellable` on the timeout path).
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param timeoutMillis timeout in milliseconds. A value of zero or less fails immediately with a
 * timeout failure, like [withTimeout].
 * @param block the work to run; it executes in a child scope with the timeout applied.
 * @return [Result.success] with the value of [block], or [Result.failure] holding the
 * [TimeoutCancellationException] when the timeout elapsed.
 * @throws CancellationException if the caller is cancelled by anything other than this timeout.
 * @see hedged
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> withTimeoutResult(
    timeoutMillis: Long,
    block: suspend CoroutineScope.() -> T,
): Result<T> {
    var nestedTimeout: TimeoutCancellationException? = null
    return try {
        Result.success(
            withTimeout(timeoutMillis) {
                try {
                    block()
                } catch (e: TimeoutCancellationException) {
                    // Our own timeout cancels this scope before the exception surfaces; if the
                    // scope is still active the timeout came from a nested withTimeout.
                    if (isActive) nestedTimeout = e
                    throw e
                }
            },
        )
    } catch (e: TimeoutCancellationException) {
        if (nestedTimeout != null) throw e
        // An outer cancellation (e.g. an outer withTimeout) must keep propagating.
        currentCoroutineContext().ensureActive()
        Result.failure(e)
    }
}

/**
 * [Duration] overload of [withTimeoutResult].
 *
 * Example:
 * ```
 * val profile = withTimeoutResult(2.seconds) { api.loadProfile() }
 * ```
 *
 * The duration is converted to whole milliseconds; a positive duration shorter than a millisecond is
 * rounded up to 1 ms, and zero or negative durations time out immediately.
 *
 * Rules: CANCEL_006, CANCEL_007. See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param timeout timeout as a [Duration].
 * @param block the work to run.
 * @return see the milliseconds overload.
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> withTimeoutResult(
    timeout: Duration,
    block: suspend CoroutineScope.() -> T,
): Result<T> = withTimeoutResult(timeout.inWholeMilliseconds.coerceAtLeast(if (timeout.isPositive()) 1 else 0), block)
