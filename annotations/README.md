# Annotations for Structured Coroutines

Multiplatform annotations for structured concurrency: marking coroutine scopes and qualifying
dispatcher dependencies for testable code.

## Installation

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
}

// For KMP projects (commonMain)
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
            }
        }
    }
}
```

## Usage

### @StructuredScope Annotation

Mark coroutine scopes that follow structured concurrency principles:

```kotlin
import io.github.santimattius.structured.annotations.StructuredScope

// Function parameter
fun loadData(@StructuredScope scope: CoroutineScope) {
    scope.launch { fetchData() }
}

// Constructor injection
class UserService(
    @StructuredScope private val scope: CoroutineScope
) {
    fun fetchUser(id: String) {
        scope.launch { /* ... */ }
    }
}

// Class property
class Repository {
    @StructuredScope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun fetchData() {
        scope.launch { /* ... */ }
    }
}
```

---

## Dispatcher Qualifier Annotations

Use these annotations to qualify `CoroutineDispatcher` dependencies so production code stays
decoupled from concrete dispatchers and test code can substitute an unconfined or test dispatcher.

### @IoDispatcher

Qualifies a dispatcher intended for blocking I/O work (maps to `Dispatchers.IO` in production).

```kotlin
import io.github.santimattius.structured.annotations.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UserRepository(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun fetchUser(id: String): User = withContext(ioDispatcher) {
        api.getUser(id)
    }
}
```

### @MainDispatcher

Qualifies a dispatcher bound to the UI / main thread (maps to `Dispatchers.Main` in production).

```kotlin
import io.github.santimattius.structured.annotations.MainDispatcher
import kotlinx.coroutines.CoroutineDispatcher

class UiViewModel(
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher
) { /* ... */ }
```

### @DefaultDispatcher

Qualifies a dispatcher for CPU-bound default work (maps to `Dispatchers.Default` in production).

```kotlin
import io.github.santimattius.structured.annotations.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher

class ImageProcessor(
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
) { /* ... */ }
```

### Testing with dispatcher qualifiers

Inject `UnconfinedTestDispatcher` (or `StandardTestDispatcher`) in tests so suspending code runs
predictably without the real dispatcher:

```kotlin
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test

class UserRepositoryTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repository = UserRepository(ioDispatcher = testDispatcher)

    @Test
    fun `fetchUser returns expected user`() = runTest(testDispatcher) {
        val user = repository.fetchUser("42")
        // assert ...
    }
}
```

### DI wiring example (Hilt / Koin)

The annotations work with any DI framework. Example with Hilt:

```kotlin
// Module
@Module @InstallIn(SingletonComponent::class)
object DispatcherModule {
    @Provides @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
```

### Annotation targets and retention

All three qualifier annotations share the same configuration:

| Target | Supported |
|--------|-----------|
| Value parameter | ✅ |
| Field / property | ✅ |
| Function | ✅ |

**Retention:** `BINARY` — present in the compiled `.class` / `.klib` for DI frameworks but not
available at runtime reflection.

---

## Supported Platforms

| Platform | Artifact |
|----------|----------|
| JVM | `annotations-jvm` |
| JS | `annotations-js` |
| iOS | `annotations-iosarm64`, `annotations-iossimulatorarm64` |
| macOS | `annotations-macosx64`, `annotations-macosarm64` |
| watchOS | `annotations-watchosarm64`, etc. |
| tvOS | `annotations-tvosarm64`, etc. |
| Linux | `annotations-linuxx64`, `annotations-linuxarm64` |
| Windows | `annotations-mingwx64` |
| WASM | `annotations-wasmjs`, `annotations-wasmwasi` |

## Recognition by compiler and IDE

The **Structured Coroutines compiler plugin** and **IntelliJ plugin** both recognize `@StructuredScope` on function parameters and class properties. For example, `fun foo(@StructuredScope scope: CoroutineScope) { scope.launch { } }` is not reported as an unstructured launch. The IDE resolves the scope name to the parameter or property declaration and checks for the annotation.

## Framework Scopes (Auto-recognized)

The following scopes are automatically recognized without annotation:

- `viewModelScope` (Android ViewModel)
- `lifecycleScope` (Android Lifecycle)
- `rememberCoroutineScope()` (Jetpack Compose)

## License

```
Copyright 2026 Santiago Mattiauda
Licensed under the Apache License, Version 2.0
```
