package io.github.santimattius.structured.runtime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Holds a piece of UI/domain state and exposes it read-only, implementing the FLOW_010 backing
 * property pattern: the [MutableStateFlow] is private, so external code can only change state
 * through [set] and [update].
 *
 * Example:
 * ```
 * class CounterViewModel {
 *     private val holder = StateHolder(0)
 *     val count: StateFlow<Int> = holder.state
 *
 *     fun increment() = holder.update { it + 1 }
 * }
 * ```
 *
 * It is a final class (not `open`): extending it could not add safety, and a final type keeps the
 * guarantee that the mutable flow is never exposed by a subclass.
 *
 * Rules: FLOW_010 (never expose `MutableStateFlow`/`MutableSharedFlow` publicly).
 * See `docs/BEST_PRACTICES_COROUTINES.md` section 9.5.
 */
@ExperimentalStructuredCoroutines
public class StateHolder<T>(initial: T) {
    private val _state = MutableStateFlow(initial)

    /** Read-only view of the state. It is not a [MutableStateFlow] and cannot be cast back to one. */
    public val state: StateFlow<T> = _state.asStateFlow()

    /** Current value. */
    public val value: T get() = _state.value

    /**
     * Atomically replaces the value with `transform(current)`. [transform] may be re-invoked if
     * another thread updates concurrently, so it must be side-effect free.
     */
    public fun update(transform: (T) -> T) {
        _state.update(transform)
    }

    /** Replaces the value. Prefer [update] when the new value depends on the current one. */
    public fun set(value: T) {
        _state.value = value
    }
}
