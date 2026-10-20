package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Compilation WARNING: misplacedTryCatchInScope (EXCEPT_004)
 *
 * The try/catch sits inside the coroutineScope lambda, around await() of a child created in that
 * same lambda. The child's failure cancels the scope and coroutineScope rethrows to its caller,
 * so the catch cannot contain it. Wrap the whole coroutineScope call in try/catch, catch inside
 * the async block, or use supervisorScope.
 */
suspend fun triggerMisplacedTryCatchInScopeWarning(): Int = coroutineScope {
    val deferred = async<Int> { error("boom") }
    try {
        deferred.await()
    } catch (e: Exception) {
        0
    }
}
