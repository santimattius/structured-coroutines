package io.github.santimattius.structured.sample.compilation

import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Compilation ERROR: jobInBuilderContext
 *
 * Passing Job() or SupervisorJob() to launch/async/withContext breaks
 * structured concurrency. Use supervisorScope { } or a proper scope instead.
 */
fun triggerJobInBuilderContextError(@StructuredScope scope: CoroutineScope) {
    scope.launch(Job()) {
        println("Bad: Job() in launch")
    }
    scope.launch(SupervisorJob()) {
        println("Bad: SupervisorJob() in launch")
    }
}

suspend fun triggerJobInWithContextError(@StructuredScope scope: CoroutineScope) {
    scope.launch {
        withContext(SupervisorJob()) {
            println("Bad: SupervisorJob() in withContext")
        }
    }
}
