# 8.2 ARCH_002 — Lifecycle-Aware Flow Collection (Android)

## Bad Practice

Collecting a Flow in Activity/Fragment with `lifecycleScope.launch { flow.collect { } }` without
tying collection to the lifecycle state. The Flow keeps running when the UI goes to background,
wasting resources and potentially updating invisible UI.

```kotlin
// BAD: Flow collects even when Activity is in background (stopped/paused)
class UserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUI(state) // runs even when screen is off
            }
        }
    }
}
```

## Recommended

Use `repeatOnLifecycle(Lifecycle.State.STARTED)` (or `flowWithLifecycle`) so collection starts
when the UI reaches the STARTED state and automatically cancels when it stops. This prevents
unnecessary work and UI updates when the screen is not visible.

```kotlin
// GOOD: collection tied to STARTED lifecycle state
class UserActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updateUI(state) // only when Activity is started
                }
            }
        }
    }
}

// GOOD alternative: flowWithLifecycle operator
lifecycleScope.launch {
    viewModel.uiState
        .flowWithLifecycle(lifecycle, Lifecycle.State.STARTED)
        .collect { state -> updateUI(state) }
}
```

## Why

`lifecycleScope` is cancelled when the Activity/Fragment is destroyed, but not when it goes to
background (STOPPED). Without `repeatOnLifecycle`, the Flow collects in background, potentially
causing memory leaks, unnecessary network calls, and updates to a UI the user cannot see.
`repeatOnLifecycle(STARTED)` automatically starts/stops collection matching the UI visibility.

## Quick fix

| Erroneous | Optimized |
|-----------|-----------|
| `lifecycleScope.launch { flow.collect { } }` | `lifecycleScope.launch { repeatOnLifecycle(STARTED) { flow.collect { } } }` |
