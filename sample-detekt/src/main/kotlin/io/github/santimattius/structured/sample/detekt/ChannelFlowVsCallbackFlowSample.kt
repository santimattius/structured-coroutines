package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class ChannelFlowVsCallbackFlowSample {
    fun sensorFlow(): Flow<Int> = channelFlow {
        trySend(1)
    }
}
