package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Demonstrates FLOW_010: MutableStateFlow/MutableSharedFlow exposed as public.
 * Used to validate that :detekt-rules report public mutable flow properties.
 *
 * BAD: mutable flows are public — any caller can emit values, breaking
 * Unidirectional Data Flow (UDF).
 */

// BAD: mutable flows are public — any caller can emit values
class BadViewModel {
    val uiState = MutableStateFlow<String>("loading")
    val events = MutableSharedFlow<String>()
}

// GOOD: backing properties with read-only public exposure
class GoodViewModel {
    private val _uiState = MutableStateFlow<String>("loading")
    val uiState: StateFlow<String> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events: SharedFlow<String> = _events.asSharedFlow()
}
