package io.github.santimattius.structured.runtime

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(ExperimentalAtomicApi::class)
private val nextSpanId = AtomicLong(0L)

@OptIn(ExperimentalAtomicApi::class)
private fun newSpanId(): Long = nextSpanId.incrementAndFetch()

/**
 * A unit of traced work, carried in the coroutine context. [TracingDispatcher] reads it from the
 * context of every dispatch and attaches [id] and [parentId] to the run events, so a backend can
 * draw parent-child links between coroutines.
 *
 * A plain context element (not a thread-context element), so it works on every target. It is
 * inherited by child coroutines like any other element. Ids are unique per process, assigned from
 * an increasing counter. Open spans with [withTraceSpan]; the library helpers `hedged`, `parallel`,
 * `supervisedAll` and `concurrentOrdered` open a child span only when the caller already has one.
 *
 * Example:
 * ```
 * withTraceSpan("checkout") {
 *     val span = coroutineContext[TraceSpan] // id, parentId = null, name = "checkout"
 * }
 * ```
 *
 * @property id identifier of this span, unique per process.
 * @property parentId [id] of the enclosing span, or null for a root span.
 * @property name label of this span, such as "checkout". Keep it low-cardinality.
 */
@ExperimentalStructuredCoroutines
public class TraceSpan internal constructor(
    public val id: Long,
    public val parentId: Long?,
    public val name: String,
) : AbstractCoroutineContextElement(Key) {

    /** Key of [TraceSpan] in a [CoroutineContext]. */
    public companion object Key : CoroutineContext.Key<TraceSpan> {
        internal fun root(name: String): TraceSpan = TraceSpan(newSpanId(), null, name)
    }

    internal fun child(name: String): TraceSpan = TraceSpan(newSpanId(), id, name)

    override fun toString(): String = "TraceSpan(id=$id, parentId=$parentId, name=$name)"
}

/**
 * Runs [block] inside a new [TraceSpan] named [name]. Its parent is the span already in the
 * current context, or none (a root span) when there is no span.
 *
 * Example:
 * ```
 * withTraceSpan("load-profile") { api.loadProfile(userId) }
 * ```
 */
@ExperimentalStructuredCoroutines
public suspend fun <T> withTraceSpan(name: String, block: suspend () -> T): T {
    val parent = currentCoroutineContext()[TraceSpan]
    val span = if (parent == null) TraceSpan.root(name) else parent.child(name)
    return withContext(span) { block() }
}

/**
 * Context for the children of a helper: a child span of the caller's span when there is one, else
 * an empty context (no span is allocated). Opt-in plumbing for `hedged`, `parallel`,
 * `supervisedAll` and `concurrentOrdered`.
 */
@ExperimentalStructuredCoroutines
internal suspend fun childSpanContext(name: String): CoroutineContext =
    currentCoroutineContext()[TraceSpan]?.child(name) ?: EmptyCoroutineContext
