package io.github.santimattius.structured.sample.compilation

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Compilation ERROR: globalScopeUsage
 *
 * GlobalScope usage is not allowed. Use @StructuredScope, viewModelScope,
 * lifecycleScope, or coroutineScope { } instead.
 */
fun triggerGlobalScopeError() {
    GlobalScope.launch {
        println("Bad: orphan coroutine")
    }
}
