package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.*

/** Triggers CONCUR_002 — parallel launch + mutable list. */
suspend fun sharedMutableSample(items: List<Int>) = coroutineScope {
    var results = mutableListOf<Int>()
    items.forEach { launch { results.add(it) } }
}
