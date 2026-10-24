package io.github.santimattius.structured.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn

/**
 * Launches the collection of this flow in [scope], routing any upstream failure to [onError]
 * instead of letting it propagate to (and cancel) the parent scope.
 *
 * It is `catch { onError(it) }.launchIn(scope)`. The `catch` operator only handles exceptions
 * thrown by the upstream and never intercepts a `CancellationException`, so cancelling [scope]
 * or the returned [Job] does not invoke [onError]. Values emitted before a failure are still
 * delivered downstream. Exceptions thrown by [onError] itself propagate normally.
 *
 * Example:
 * ```
 * repository.getItems()
 *     .map { it.toUiModel() }
 *     .onEach { _state.value = it }
 *     .launchInSafely(viewModelScope) { e -> _error.value = e.message }
 * ```
 *
 * Rules: FLOW_005 (missing `catch` in a Flow chain).
 * See `docs/BEST_PRACTICES_COROUTINES.md` section 9.6.
 */
@ExperimentalStructuredCoroutines
public fun <T> Flow<T>.launchInSafely(scope: CoroutineScope, onError: (Throwable) -> Unit): Job =
    catch { onError(it) }.launchIn(scope)

/**
 * Converts this flow into a [StateFlow] that only runs upstream while it has subscribers,
 * plus a [stopTimeoutMillis] grace period after the last one leaves.
 *
 * It is `stateIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis, replayExpirationMillis), initialValue)`.
 * The default of 5_000 ms is the value recommended by FLOW_006: it keeps work alive across short
 * interruptions such as Android configuration changes, without keeping upstream running forever
 * like `SharingStarted.Eagerly`. [initialValue] is visible before the first upstream emission,
 * and upstream restarts when a new subscriber appears after it stopped.
 *
 * Example:
 * ```
 * val items: StateFlow<List<Item>> = repository.observeItems()
 *     .stateInWhileSubscribed(viewModelScope, initialValue = emptyList())
 * ```
 *
 * Rules: FLOW_006 (`stateIn` with `Eagerly` on a lifecycle scope).
 * See `docs/BEST_PRACTICES_COROUTINES.md` section 9.7.
 */
@ExperimentalStructuredCoroutines
public fun <T> Flow<T>.stateInWhileSubscribed(
    scope: CoroutineScope,
    initialValue: T,
    stopTimeoutMillis: Long = 5_000,
    replayExpirationMillis: Long = Long.MAX_VALUE,
): StateFlow<T> = stateIn(
    scope,
    SharingStarted.WhileSubscribed(stopTimeoutMillis, replayExpirationMillis),
    initialValue,
)
