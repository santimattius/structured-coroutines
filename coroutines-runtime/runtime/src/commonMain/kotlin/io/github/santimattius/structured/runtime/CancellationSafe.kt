package io.github.santimattius.structured.runtime

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Like [runCatching], but never swallows a [CancellationException].
 *
 * Any other [Throwable] thrown by [block] is captured into the returned [Result].
 * A [CancellationException] is rethrown immediately, so cooperative cancellation keeps
 * propagating through the coroutine hierarchy instead of being turned into a failure value.
 *
 * Example:
 * ```
 * val user = runCatchingCancellable { repository.fetchUser(id) }
 *     .getOrElse { fallbackUser }
 * ```
 *
 * The function is `inline`, so [block] may contain non-local returns and suspend calls when
 * invoked from a suspend context. It is the safe replacement for [runCatching] in suspending code.
 *
 * Rule: CANCEL_003 (do not catch `CancellationException` without rethrowing).
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param block the computation to run.
 * @return [Result.success] with the value of [block], or [Result.failure] with any non-cancellation
 * [Throwable] it threw.
 * @throws CancellationException if [block] throws one (rethrown unchanged).
 * @see withCleanup
 */
@ExperimentalStructuredCoroutines
public inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Receiver variant of [runCatchingCancellable]: runs [block] with `this` as its receiver,
 * capturing ordinary exceptions into a [Result] and rethrowing [CancellationException].
 *
 * Example:
 * ```
 * val size = file.runCatchingCancellable { readBytes().size }
 * ```
 *
 * Rule: CANCEL_003 (do not catch `CancellationException` without rethrowing).
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @receiver the object passed to [block] as `this`.
 * @param block the computation to run with the receiver as `this`.
 * @return [Result.success] with the value of [block], or [Result.failure] with any non-cancellation
 * [Throwable] it threw.
 * @throws CancellationException if [block] throws one (rethrown unchanged).
 */
@ExperimentalStructuredCoroutines
public inline fun <T, R> T.runCatchingCancellable(block: T.() -> R): Result<R> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

/**
 * Runs [block] and then always runs [cleanup] inside `withContext(NonCancellable)`,
 * so cleanup is allowed to suspend even when the coroutine is already cancelled.
 *
 * [cleanup] runs after [block] succeeds, throws, or is cancelled. If [block] failed and
 * [cleanup] also throws, the exception from [block] is kept and the cleanup exception is
 * attached with [addSuppressed][Throwable.addSuppressed]. If only [cleanup] throws, that
 * exception propagates.
 *
 * Example:
 * ```
 * withCleanup(cleanup = { session.close() }) {
 *     session.send(request)
 * }
 * ```
 *
 * Cancellation of [block] is not swallowed: after [cleanup] has run, the original
 * [CancellationException] is rethrown like any other exception from [block].
 *
 * Rule: CANCEL_004 (cleanup in `finally` that suspends must use `NonCancellable`).
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @param cleanup suspending cleanup, always executed in a [NonCancellable] context.
 * @param block the main work.
 * @return the value returned by [block].
 * @throws Throwable whatever [block] threw (including [CancellationException]), or the exception
 * from [cleanup] when [block] succeeded.
 * @see runCatchingCancellable
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> withCleanup(
    cleanup: suspend () -> Unit,
    block: suspend () -> T,
): T {
    val result =
        try {
            block()
        } catch (blockError: Throwable) {
            try {
                withContext(NonCancellable) { cleanup() }
            } catch (cleanupError: Throwable) {
                if (cleanupError !== blockError) blockError.addSuppressed(cleanupError)
            }
            throw blockError
        }
    withContext(NonCancellable) { cleanup() }
    return result
}
