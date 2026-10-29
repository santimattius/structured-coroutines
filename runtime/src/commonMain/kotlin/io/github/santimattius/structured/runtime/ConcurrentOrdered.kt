package io.github.santimattius.structured.runtime

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Server-side ordered fan-in: when the returned flow is collected, launches every task
 * concurrently with `async` and then awaits them in input order, emitting each result as soon
 * as its turn is ready.
 *
 * Unlike `channelFlow { tasks.forEach { launch { send(it()) } } }` (fan-in), results are never
 * interleaved: they are emitted in input order. Unlike [parallel], which returns only after
 * ALL tasks finish, the first result is emitted as soon as the first task completes, so the
 * consumer gets an early first byte while total latency stays that of the slowest task.
 *
 * The returned flow is cold: no task starts until it is collected, and each collection runs
 * the tasks again. Concurrency starts at collection, not at construction. Fail-fast: the first
 * task failure cancels the remaining tasks and is rethrown to the collector; completing or
 * cancelling the collection early cancels pending tasks.
 *
 * When the collector's context has a [TraceSpan], the tasks run in one child span named
 * "concurrentOrdered"; otherwise nothing changes.
 *
 * Example:
 * ```
 * listOf({ search() }, { loadImage() }, { loadFiles() })
 *     .concurrentOrdered()
 *     .collect { part -> response.write(part) }
 * ```
 *
 * Pattern: fan-in with server-side ordering (`async` + ordered `await`).
 * Rules: CONCUR_003 (launch all `async` jobs before awaiting). See `docs/BEST_PRACTICES_COROUTINES.md`.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines].
 *
 * @receiver the task factories; snapshotted into a list when this function is called.
 * @return a cold [Flow] that emits one result per task, in input order. An empty receiver yields
 * an empty flow.
 * @see parallel
 */
@ExperimentalStructuredCoroutines
public fun <T> Iterable<suspend () -> T>.concurrentOrdered(): Flow<T> {
    val tasks = toList()
    return flow {
        val span = childSpanContext("concurrentOrdered")
        coroutineScope {
            val deferred = tasks.map { task -> async(span) { task() } }
            for (d in deferred) emit(d.await())
        }
    }
}
