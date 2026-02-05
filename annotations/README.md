# Annotations for Structured Coroutines

Multiplatform annotations for marking structured coroutine scopes.

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
