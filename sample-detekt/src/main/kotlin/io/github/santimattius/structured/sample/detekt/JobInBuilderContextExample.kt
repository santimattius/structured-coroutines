package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: JobInBuilderContext (DISPATCH_004).
 * Used to validate that :detekt-rules report Job()/SupervisorJob() in builders.
 */
fun triggerJobInBuilderContext(scope: CoroutineScope) {
    scope.launch(Job()) {
        println("Orphan")
    }
}
