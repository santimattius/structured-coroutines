package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Compilation ERROR: runBlockingInSuspend
 *
 * runBlocking should not be called inside a suspend function.
 * Use withContext or other suspending alternatives instead.
 */
suspend fun triggerRunBlockingInSuspendError() {
    runBlocking {
        delay(100)
    }
}
