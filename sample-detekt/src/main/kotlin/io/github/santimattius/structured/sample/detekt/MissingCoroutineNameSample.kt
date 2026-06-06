package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Demonstrates DEBUG_001 (opt-in): unnamed [launch] hurts debugging.
 * Enable `MissingCoroutineName` in detekt config to see the report.
 */

@Suppress("GlobalScopeUsage")
fun badFireAndForget() {
    GlobalScope.launch {
        work()
    }
}

private fun work() {}
