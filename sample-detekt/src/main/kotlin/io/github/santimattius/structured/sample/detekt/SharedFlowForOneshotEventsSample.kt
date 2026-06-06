package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.flow.MutableSharedFlow

/** Demonstrates FLOW_011: default [MutableSharedFlow] for one-shot UI events. */

class BadEventsViewModel {
    private val _events = MutableSharedFlow<UiEvent>()
}

private sealed interface UiEvent
