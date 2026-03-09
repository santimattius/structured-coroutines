# 9.4 FLOW_004 — SharedFlow Configuration

## Bad Practice

Creating `MutableSharedFlow` with default configuration without considering slow subscribers,
replay needs, or backpressure. Default config (`replay=0`, no buffer) can lead to dropped events
or a suspended emitter when there are no active subscribers.

```kotlin
// BAD: default SharedFlow; events emitted before subscription are lost;
// slow subscribers block the emitter
private val _events = MutableSharedFlow<UiEvent>() // replay=0, buffer=0

// Emitter suspends if no subscriber or subscriber is slow:
_events.emit(UiEvent.ShowError("oops")) // may suspend forever
```

## Recommended

Configure `replay`, `extraBufferCapacity`, and `onBufferOverflow` according to whether you need
recent values for new subscribers and how to handle overflow. StateFlow (replay=1, conflated) is
often the right choice for state. SharedFlow is for events with configurable delivery guarantees.

```kotlin
// GOOD: SharedFlow for events — buffer so emitter never suspends; drop oldest on overflow
private val _events = MutableSharedFlow<UiEvent>(
    replay = 0,                              // no replay for one-shot events
    extraBufferCapacity = 64,               // buffer events while subscribers are slow
    onBufferOverflow = BufferOverflow.DROP_OLDEST // drop old events rather than suspending
)

// GOOD: replay=1 when late subscribers need the last value (similar to StateFlow)
private val _status = MutableSharedFlow<Status>(replay = 1)

// GOOD: StateFlow — effectively SharedFlow(replay=1, DROP_OLDEST, conflated)
private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
```

## Why

The default `MutableSharedFlow()` has no buffer and no replay, meaning the emitter suspends when
there are no active subscribers or subscribers are slower than the emitter. This causes unexpected
coroutine suspension and dropped events. Explicit configuration communicates intent and prevents
subtle bugs. Use `StateFlow` for UI state (always has a value, conflated), and configure
`SharedFlow` explicitly when event delivery guarantees matter.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `MutableSharedFlow<Event>()` (defaults) | `MutableSharedFlow(replay = 0, extraBufferCapacity = 64, onBufferOverflow = DROP_OLDEST)` for fire-and-collect events; `MutableStateFlow(initial)` for state |
