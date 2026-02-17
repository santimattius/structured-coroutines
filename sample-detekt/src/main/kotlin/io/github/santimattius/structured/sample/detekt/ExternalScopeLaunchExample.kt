package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: ExternalScopeLaunch.
 * Used to validate that :detekt-rules report launching on external scope from suspend function.
 */
class ServiceWithExternalScope(private val scope: CoroutineScope) {

    suspend fun process() {
        scope.launch {
            delay(1)
        }
    }
}
