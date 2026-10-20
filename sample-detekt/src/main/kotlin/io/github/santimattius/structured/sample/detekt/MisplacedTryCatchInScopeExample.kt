package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope

/**
 * Intentionally triggers Detekt rule: MisplacedTryCatchInScope (EXCEPT_004).
 * The async child is a structural child of coroutineScope, so the catch cannot stop its failure
 * from propagating out of the scope.
 */
suspend fun triggerMisplacedTryCatchInScope(): Int = coroutineScope {
    val d = async<Int> { error("boom") }
    try {
        d.await()
    } catch (e: IllegalStateException) {
        0
    }
}

/** GOOD: wrap the whole coroutineScope call. */
suspend fun wrapWholeScope(): Int =
    try {
        coroutineScope { async<Int> { error("boom") }.await() }
    } catch (e: IllegalStateException) {
        0
    }

/** GOOD: catch inside the child. */
suspend fun catchInsideChild(): Int = coroutineScope {
    async {
        try {
            error("boom")
        } catch (e: IllegalStateException) {
            0
        }
    }.await()
}

/** GOOD: supervisorScope isolates its direct children. */
suspend fun useSupervisorScope(): Int = supervisorScope {
    val d = async<Int> { error("boom") }
    try {
        d.await()
    } catch (e: IllegalStateException) {
        0
    }
}
