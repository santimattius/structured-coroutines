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
 *     suspend fun onLoggedIn() { _events.send(LoginEvent.NavigateHome) }
 * }
 * ```
 *
 * Prefer [send]. [trySend] returns `false` when the buffer is full or the channel is closed and
 * the event is then NOT delivered, so check its result if losing an event matters.
 *
 * Delivery is at-most-once per event: an event that was already handed to a collector which is
 * cancelled before it processes it (for example a lifecycle-aware collector stopping) is lost.
 * Pass [onUndeliveredElement] to observe such events, e.g. to re-queue or log them.
 *
 * Single-consumer semantics: each event is delivered to exactly one collector. With several
 * concurrent collectors events are distributed among them, not broadcast (unlike `SharedFlow`).
 * Use a single collector, or a `MutableSharedFlow` when every subscriber must see every event.
 *
 * Rules: FLOW_011 (no replay-0 SharedFlow for one-shot events).
 * See `docs/BEST_PRACTICES_COROUTINES.md` section 9.11.
 *
 * Requires opting in to [ExperimentalStructuredCoroutines]. Thread-safe: [send] and [trySend] may be
 * called from any thread or coroutine.
 *
 * @param T the event type.
 * @param capacity buffer capacity, [Channel.BUFFERED] by default. When the buffer is full [send]
 * suspends and [trySend] returns `false`, so the sender always learns about a rejected event.
 * @param onUndeliveredElement called with an event that was accepted by the channel but could not be
 * delivered: its collector was cancelled first, or the channel was closed without being drained.
 * It may run concurrently and must not throw. `null` by default (such events are dropped).
 * @see StateHolder
 */
@ExperimentalStructuredCoroutines
public class EventChannel<T>(
    capacity: Int = Channel.BUFFERED,
    onUndeliveredElement: ((T) -> Unit)? = null,
) {
    private val channel = Channel<T>(capacity, onUndeliveredElement = onUndeliveredElement)

    /**
     * Events as a flow backed by [receiveAsFlow]; each event is received by exactly one collector.
     * Cancelling a collector does not close the channel.
     */
    public val events: Flow<T> = channel.receiveAsFlow()

    /**
     * Sends [event], suspending while the buffer is full.
     *
     * @throws kotlinx.coroutines.channels.ClosedSendChannelException if the channel was closed with [close].
     * @throws kotlin.coroutines.cancellation.CancellationException if the calling coroutine is cancelled while suspended.
     */
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
