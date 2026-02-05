package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Compilation ERROR: unstructuredLaunch
 *
 * Launch on a scope not annotated with @StructuredScope.
 * Annotate the scope parameter with @StructuredScope or use structured builders.
 */
fun triggerUnstructuredLaunchError(scope: CoroutineScope) {
    scope.launch {
        println("Bad: unstructured launch")
    }
}
