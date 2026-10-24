package io.github.santimattius.structured.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs every suspending task concurrently and returns the results in input order.
 *
 * Fail-fast: the first task failure cancels the remaining tasks and is rethrown
 * (`coroutineScope` + `async` + `awaitAll`). Use [supervisedAll] to isolate failures.
 *
 * [concurrency] limits how many tasks run at once (default: unlimited); it must be greater
 * than zero.
 *
 * Example:
 * ```
 * val (first, second) = listOf<suspend () -> Item>({ repo.a() }, { repo.b() }).parallel()
 * ```
 *
 * Rules: CONCUR_003 (launch all `async` jobs before awaiting), SCOPE_004 (first failure
 * cancels the rest in `coroutineScope`).
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> Iterable<suspend () -> T>.parallel(
    concurrency: Int = Int.MAX_VALUE,
): List<T> {
    require(concurrency > 0) { "concurrency must be > 0 but was $concurrency" }
    val permits = Semaphore(concurrency)
    return coroutineScope {
        map { task -> async { permits.withPermit { task() } } }.awaitAll()
    }
}

/**
 * Runs every suspending task concurrently, isolating failures, and returns one [Result] per
 * task in input order (`supervisorScope` + `async`).
 *
 * A failing task never cancels its siblings. If the caller itself is cancelled, the
 * [CancellationException] is rethrown instead of being captured. A task that throws its own
 * [CancellationException] while the caller is still active is captured as a failure.
 *
 * [concurrency] limits how many tasks run at once (default: unlimited); it must be greater
 * than zero.
 *
 * Example:
 * ```
 * val results = listOf<suspend () -> Item>({ api.a() }, { api.b() }).supervisedAll()
 * val ok = results.mapNotNull { it.getOrNull() }
 * ```
 *
 * Rules: SCOPE_004 (use `supervisorScope` for independent failure semantics), CONCUR_003.
 * See `docs/BEST_PRACTICES_COROUTINES.md`.
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> Iterable<suspend () -> T>.supervisedAll(
    concurrency: Int = Int.MAX_VALUE,
): List<Result<T>> {
    require(concurrency > 0) { "concurrency must be > 0 but was $concurrency" }
    val permits = Semaphore(concurrency)
    return supervisorScope {
        map { task ->
            async {
                try {
                    Result.success(permits.withPermit { task() })
                } catch (e: CancellationException) {
                    // Rethrows when the caller was cancelled; otherwise the task cancelled itself.
                    currentCoroutineContext().ensureActive()
                    Result.failure(e)
                } catch (e: Throwable) {
                    Result.failure(e)
                }
            }
        }.awaitAll()
    }
}
