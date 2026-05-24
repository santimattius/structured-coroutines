package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.flow.*

/** Triggers FLOW_008 when rule is active. */
fun sideEffectInMap(flow: Flow<Int>) = flow.map { item ->
    println("side effect")
    item * 2
}
