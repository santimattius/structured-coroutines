# Spring + Coroutines

## Blocking and transactions

- Blocking JDBC in `@Transactional suspend fun` blocks a thread — prefer **R2DBC** for reactive/suspend stacks.
- Same blocking rules as Ktor: `withContext(Dispatchers.IO) { }` for legacy JDBC (`BACKEND_001`).

## MDC

Propagate SLF4J MDC with `kotlinx-coroutines-slf4j` (`BACKEND_002`):

```kotlin
withContext(Dispatchers.IO + MDCContext()) {
    service.load()
}
```

## Gradle preset

```kotlin
structuredCoroutines {
    useSpringBackendProfile()
}
```

Uses `spring-backend-detekt.yml` (includes iter-3 Detekt rules when enabled in the preset file).
