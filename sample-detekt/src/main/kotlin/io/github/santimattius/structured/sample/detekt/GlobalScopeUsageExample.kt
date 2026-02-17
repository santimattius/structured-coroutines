package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Intentionally triggers Detekt rule: GlobalScopeUsage (SCOPE_001).
 * Used to validate that :detekt-rules report GlobalScope usage.
 */
fun triggerGlobalScopeUsage() {
    GlobalScope.launch {
        println("Orphan coroutine")
    }
}
