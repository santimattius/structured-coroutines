# 9.2 FLOW_002 — Cold vs Hot Flows (StateFlow / SharedFlow)

## Bad Practice

Using a cold `Flow` for shared state without understanding that each collector triggers a new
execution. Or using hot flows (StateFlow/SharedFlow) for one-shot events without configuring
replay/buffer appropriately.

```kotlin
// BAD: each collector triggers a separate network request
fun getUserFlow(): Flow<User> = flow {
    emit(api.fetchUser()) // called once per collector — expensive!
}

// BAD: SharedFlow with defaults drops events for slow/late subscribers
val events = MutableSharedFlow<UiEvent>() // replay=0, no buffer; events lost
```

## Recommended

Use **StateFlow** for state (UI state, ViewModel state); it always has a current value and replays
it to new collectors. Use **SharedFlow** for events (one-shot, multiple subscribers) with
appropriate `replay`, `extraBufferCapacity`, and `onBufferOverflow`. Avoid cold Flow for shared
state without caching.

```kotlin
// GOOD: StateFlow — shared state, always replays last value to new collectors
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
val uiState: StateFlow<UiState> = _uiState.asStateFlow()

// GOOD: SharedFlow for events with buffer to handle slow subscribers
private val _events = MutableSharedFlow<UiEvent>(
    replay = 0,
    extraBufferCapacity = 16,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
val events: SharedFlow<UiEvent> = _events.asSharedFlow()

// GOOD: cold Flow cached with shareIn when shared state from repository
val cachedFlow: SharedFlow<Data> = coldFlow
    .shareIn(scope, started = SharingStarted.WhileSubscribed(5_000), replay = 1)
```

## Why

Cold flows re-execute for each collector, which is expensive for shared state (repeated network
calls, repeated DB queries). Hot flows (StateFlow/SharedFlow) compute once and multicast. Without
correct `replay`/`extraBufferCapacity` configuration, SharedFlow drops events for late or slow
subscribers. StateFlow is the correct choice when the latest value matters; SharedFlow when
events are one-shot. Understanding the hot/cold distinction prevents both performance problems and
subtle event-loss bugs.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| Cold `flow { api.fetch() }` shared across collectors | `StateFlow` / `SharedFlow` or `flow.shareIn(scope, WhileSubscribed, replay = 1)` |
