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
 * Rule: CANCEL_003 (do not catch `CancellationException` without rethrowing).
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
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
 * Rule: CANCEL_004 (cleanup in `finally` that suspends must use `NonCancellable`).
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> withCleanup(
    cleanup: suspend () -> Unit,
    block: suspend () -> T,
): T {
    val result = try {
        block()
    } catch (blockError: Throwable) {
        try {
            withContext(NonCancellable) { cleanup() }
        } catch (cleanupError: Throwable) {
            blockError.addSuppressed(cleanupError)
        }
        throw blockError
    }
    withContext(NonCancellable) { cleanup() }
    return result
}
