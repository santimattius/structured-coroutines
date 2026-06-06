# Compose + Coroutines Integration

## Collect UI state

- **Android screens:** `collectAsStateWithLifecycle()` (`COMPOSE_001`) — requires `lifecycle-runtime-compose`.
- **Desktop / shared UI:** `collectAsState()` may be intentional when lifecycle APIs are unavailable.

## Effects vs scopes

- **Initialization / keyed work:** `LaunchedEffect(key) { }` (`COMPOSE_002`).
- **User actions:** `rememberCoroutineScope().launch { }` inside handlers.
- **Side effects (analytics, logging):** `SideEffect` / `LaunchedEffect` — not in composable body (`COMPOSE_003`).

## Gradle

```kotlin
structuredCoroutines {
    useAndroidComposeProfile()
}
```

See [BEST_PRACTICES §8](../BEST_PRACTICES_COROUTINES.md#8-architecture-patterns).
