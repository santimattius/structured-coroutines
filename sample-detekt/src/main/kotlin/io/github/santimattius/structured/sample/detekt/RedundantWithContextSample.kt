package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.*

/** Triggers CONCUR_004 when rule is active in detekt.yml. */
suspend fun redundantWithContext(io: CoroutineDispatcher) = withContext(io) {
    withContext(io) { 42 }
}
