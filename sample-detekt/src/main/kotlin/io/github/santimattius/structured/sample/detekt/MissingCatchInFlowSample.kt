package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Demonstrates FLOW_005: Missing catch operator in Flow chain.
 * Used to validate that :detekt-rules report Flow chains without .catch before terminal operator.
 *
 * BAD: no catch — exceptions from intermediate operators propagate to the
 * containing scope and cancel it.
 */

val scope = CoroutineScope(Dispatchers.Default)
val _state = MutableStateFlow<String?>(null)
val _error = MutableStateFlow<String?>(null)

fun getItems() = flow { emit("item") }

// BAD: no catch — exceptions propagate to scope and cancel it
fun badCollect() {
    scope.launch {
        getItems()
            .map { it.uppercase() }
            .collect { _state.value = it }
    }
}

// GOOD: catch before collect
fun goodCollect() {
    scope.launch {
        getItems()
            .map { it.uppercase() }
            .catch { e -> _error.value = e.message }
            .collect { _state.value = it }
    }
}
