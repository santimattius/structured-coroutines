# Ktor + Coroutines

## Blocking work

Wrap JDBC, file I/O, and `Thread.sleep` in `withContext(Dispatchers.IO) { }` (`BACKEND_001`).

## MDC / logging

Use `MDCContext()` when switching dispatchers (`BACKEND_002`):

```kotlin
withContext(Dispatchers.IO + MDCContext()) {
    repository.load()
}
```

## Gradle preset

```kotlin
structuredCoroutines {
    useKtorBackendProfile()
}
```

Copy `ktor-backend-detekt.yml` from the plugin JAR into your Detekt config.

See [release v0.9.0](../releases/v0.9.0.md).
