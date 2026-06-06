# Kotlin Multiplatform + Coroutines

## Dispatchers

| Platform | `Dispatchers.IO` | Recommendation |
|----------|------------------|----------------|
| JVM / Android | ✅ | `@IoDispatcher` injection or `expect/actual` |
| iOS / JS / Native (common) | ❌ | `Dispatchers.Default` + injected IO dispatcher on JVM only |

Rule: **`KMP_001`** — never reference `Dispatchers.IO` in `commonMain`.

## runBlocking

Avoid **`KMP_002`** in `commonMain` / `commonTest`. Expose `suspend` APIs; use platform entry points for blocking bridges.

## MainScope

**`KMP_003`:** cancel `MainScope()` in `onDestroy` / `onCleared` / `dispose`.

```kotlin
structuredCoroutines {
    useKmpCommonProfile()
}
```
