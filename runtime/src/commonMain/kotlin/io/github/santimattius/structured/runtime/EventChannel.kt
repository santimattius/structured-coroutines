package io.github.santimattius.structured.runtime

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * One-shot events (navigation, snackbars) that must not be lost when nobody is collecting yet and
 * must be handled once. Backed by a [Channel] with a buffer, per FLOW_011 (a default
 * `MutableSharedFlow` with replay = 0 drops events emitted before a collector attaches).
 *
 * Example:
 * ```
 * class LoginViewModel {
 *     private val _events = EventChannel<LoginEvent>()
 *     val events: Flow<LoginEvent> = _events.events
 *
 *     fun onLoggedIn() { _events.trySend(LoginEvent.NavigateHome) }
 * }
 * ```
 *
 * Single-consumer semantics: each event is delivered to exactly one collector. With several
 * concurrent collectors events are distributed among them, not broadcast (unlike `SharedFlow`).
 * Use a single collector, or a `MutableSharedFlow` when every subscriber must see every event.
 *
 * Rules: FLOW_011 (no replay-0 SharedFlow for one-shot events).
 * See `docs/BEST_PRACTICES_COROUTINES.md` section 9.11.
 *
 * @param capacity buffer capacity, [Channel.BUFFERED] by default. When the buffer is full [send]
 * suspends and [trySend] returns `false`; events are never silently dropped.
 */
@ExperimentalStructuredCoroutines
public class EventChannel<T>(capacity: Int = Channel.BUFFERED) {
    private val channel = Channel<T>(capacity)

    /** Events as a cold-looking flow; each event is received by exactly one collector. */
    public val events: Flow<T> = channel.receiveAsFlow()

    /** Sends [event], suspending while the buffer is full. Fails if the channel is closed. */
    public suspend fun send(event: T) {
        channel.send(event)
    }

    /** Sends [event] without suspending. Returns `true` if accepted, `false` if full or closed. */
    public fun trySend(event: T): Boolean = channel.trySend(event).isSuccess

    /** Closes the channel: buffered events are still delivered, then [events] completes. */
    public fun close() {
        channel.close()
    }
}
