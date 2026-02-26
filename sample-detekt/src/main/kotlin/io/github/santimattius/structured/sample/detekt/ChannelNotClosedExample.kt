package io.github.santimattius.structured.sample.detekt

import kotlinx.coroutines.channels.Channel

/**
 * Intentionally triggers Detekt rule: ChannelNotClosed (CHANNEL_001).
 * Used to validate that :detekt-rules report Channel() without close() in same function.
 */
fun triggerChannelNotClosed() {
    val ch = Channel<Int>()
    ch.trySend(1)
}
