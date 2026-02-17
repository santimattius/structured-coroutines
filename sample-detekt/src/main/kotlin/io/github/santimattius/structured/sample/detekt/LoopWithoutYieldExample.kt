package io.github.santimattius.structured.sample.detekt

/**
 * Intentionally triggers Detekt rule: LoopWithoutYield.
 * Used to validate that :detekt-rules report loops without cooperation points in suspend functions.
 */
suspend fun triggerLoopWithoutYield(items: List<Int>) {
    for (item in items) {
        heavyWork(item)
    }
}

@Suppress("UnusedParameter")
private suspend fun heavyWork(item: Int) {
    // No yield/ensureActive/delay inside the loop
}
