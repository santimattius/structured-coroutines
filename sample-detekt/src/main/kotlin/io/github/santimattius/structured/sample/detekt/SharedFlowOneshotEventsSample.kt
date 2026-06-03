package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.flow.MutableSharedFlow

class SharedFlowOneshotEventsSample {
    private val _events = MutableSharedFlow<String>()
}
