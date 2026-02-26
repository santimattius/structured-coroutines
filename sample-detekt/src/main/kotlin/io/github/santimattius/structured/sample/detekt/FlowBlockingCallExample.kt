package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.flow.flow

/**
 * Intentionally triggers Detekt rule: FlowBlockingCall (FLOW_001).
 * Used to validate that :detekt-rules report blocking calls inside flow { }.
 */
fun triggerFlowBlockingCall() {
    flow {
        Thread.sleep(100)
        emit(1)
    }
}
