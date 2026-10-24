package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration

private sealed interface HedgeEvent<out T> {
    class Finished<T>(val result: Result<T>) : HedgeEvent<T>
    class Tick(val generation: Int) : HedgeEvent<Nothing>
}

/**
 * Hedged request: starts the primary attempt and, whenever the latest attempt has not
 * completed within [delay], starts another identical attempt (up to [maxAttempts] in total).
 * Returns the FIRST SUCCESSFUL result and cancels every attempt still running, so slower
 * redundant requests never waste resources.
 *
 * [block] receives the zero-based attempt index.
 *
 * Failure semantics:
 * - A failing attempt never wins. If every started attempt has failed and attempts remain,
 *   the next one starts immediately instead of waiting for [delay].
 * - If a failure happens while another attempt is still running, nothing is launched early;
 *   the regular [delay] schedule continues.
 * - If all [maxAttempts] attempts fail, the FIRST failure (by completion order) is rethrown
 *   with the remaining failures attached via `addSuppressed`.
 * - A [CancellationException] thrown by an attempt while the caller is still active counts as
 *   an ordinary failure. Cancelling the caller cancels all attempts and propagates.
 *
 * All attempts run in a child `coroutineScope`, so none outlives this call.
 *
 * Example:
 * ```
 * val profile = hedged(delay = 50.milliseconds, maxAttempts = 3) { attempt ->
 *     api.loadProfile(userId)
 * }
 * ```
 *
 * Pattern: hedged requests (redundant identical requests, first success wins, the rest are
 * cancelled by structured concurrency) to mitigate slow or flaky dependencies.
 * Not tied to a lint rule: it is a server-side pattern, not a best-practice diagnostic.
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> hedged(
    delay: Duration,
    maxAttempts: Int = 2,
    block: suspend (attempt: Int) -> T,
): T {
    require(maxAttempts > 0) { "maxAttempts must be > 0 but was $maxAttempts" }
    require(!delay.isNegative()) { "delay must not be negative but was $delay" }
    return coroutineScope {
        val events = Channel<HedgeEvent<T>>(Channel.UNLIMITED)
        var launched = 0
        var generation = 0
        var timer: Job? = null

        fun startAttempt() {
            val index = launched++
            launch {
                val result = try {
                    Result.success(block(index))
                } catch (e: CancellationException) {
                    // Rethrow when the caller/scope was cancelled; otherwise the attempt cancelled itself.
                    if (!isActive) throw e
                    Result.failure(e)
                } catch (e: Throwable) {
                    Result.failure(e)
                }
                events.send(HedgeEvent.Finished(result))
            }
            timer?.cancel()
            if (launched < maxAttempts) {
                val current = ++generation
                timer = launch {
                    delay(delay)
                    events.send(HedgeEvent.Tick(current))
                }
            }
        }

        startAttempt()
        var finished = 0
        var firstFailure: Throwable? = null
        var winner: Result<T>? = null
        while (winner == null) {
            when (val event = events.receive()) {
                is HedgeEvent.Tick -> if (event.generation == generation && launched < maxAttempts) startAttempt()
                is HedgeEvent.Finished -> {
                    if (event.result.isSuccess) {
                        winner = event.result
                        continue
                    }
                    val failure = event.result.exceptionOrNull()!!
                    finished++
                    val first = firstFailure
                    if (first == null) firstFailure = failure else first.addSuppressed(failure)
                    if (finished == launched) {
                        if (launched < maxAttempts) startAttempt() else throw firstFailure
                    }
                }
            }
        }
        // Cancel the losing attempts and timers before leaving the scope.
        coroutineContext.cancelChildren()
        winner.getOrThrow()
    }
}
