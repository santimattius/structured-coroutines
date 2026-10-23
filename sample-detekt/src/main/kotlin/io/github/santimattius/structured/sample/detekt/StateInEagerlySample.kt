package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.EmptyCoroutineContext

/** Triggers FLOW_006 — Eagerly on a lifecycle-named scope. */
fun stateInEagerlySample() {
    val viewModelScope = CoroutineScope(EmptyCoroutineContext)
    flowOf(1).stateIn(viewModelScope, SharingStarted.Eagerly, 0)
}
