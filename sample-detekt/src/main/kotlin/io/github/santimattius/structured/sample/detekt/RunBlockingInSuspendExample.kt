package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Intentionally triggers Detekt rule: RunBlockingInSuspend (DISPATCH_004).
 * Used to validate that :detekt-rules report runBlocking inside suspend functions.
 */
suspend fun triggerRunBlockingInSuspend() {
    runBlocking {
        delay(1)
    }
}
