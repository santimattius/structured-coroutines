# Structured Coroutines

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-Supported-orange.svg)](https://kotlinlang.org/docs/multiplatform.html)

A Kotlin Compiler Plugin that enforces **structured concurrency** rules for Kotlin Coroutines, inspired by Swift Concurrency. It detects unsafe coroutine patterns at compile-time, emitting **errors** (not warnings) to prevent common pitfalls.

## 🎯 Purpose

Kotlin Coroutines are powerful but can be misused, leading to:
- **Resource leaks** from orphaned coroutines
- **Uncontrolled lifecycle** with `GlobalScope`
- **Difficult debugging** due to scattered coroutine launches
- **Deadlocks** from `runBlocking` in suspend functions
- **Broken cancellation** from swallowed `CancellationException`

This plugin enforces structured concurrency best practices at compile time, making your concurrent code safer, more predictable, and easier to maintain.

## ✨ Features

- 🔍 **Compile-time detection** of unsafe coroutine patterns
- 🚫 **Error-level diagnostics** for critical violations
- ⚠️ **Warning-level diagnostics** for code smells
- 🎯 **Opt-in model** via `@StructuredScope` annotation
- 🤖 **Framework-aware** - recognizes Android/Compose lifecycle scopes
- 🔧 **K2/FIR compatible** - works with Kotlin 2.3+
- 📦 **Zero runtime overhead** - all checks happen at compile time
- 🌍 **Kotlin Multiplatform** - supports JVM, JS, Native, WASM

## 🚨 Rules Enforced

### Errors (Block Compilation)

| Rule | Description | Best Practice |
|------|-------------|---------------|
| `GLOBAL_SCOPE_USAGE` | Prohibits `GlobalScope.launch/async` | 1.1 |
| `INLINE_COROUTINE_SCOPE` | Prohibits `CoroutineScope(Dispatchers.X).launch` | 1.3 |
| `UNSTRUCTURED_COROUTINE_LAUNCH` | Requires structured scope | 1.1 |
| `RUN_BLOCKING_IN_SUSPEND` | Prohibits `runBlocking` in suspend functions | 2.2 |
| `JOB_IN_BUILDER_CONTEXT` | Prohibits `Job()`/`SupervisorJob()` in builders | 3.3, 5.1 |
| `CANCELLATION_EXCEPTION_SUBCLASS` | Prohibits extending `CancellationException` | 5.2 |

### Warnings (Allow Compilation)

| Rule | Description | Best Practice |
|------|-------------|---------------|
| `DISPATCHERS_UNCONFINED_USAGE` | Warns about `Dispatchers.Unconfined` | 3.2 |
| `SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE` | Warns about unprotected suspend in finally | 4.3 |
| `CANCELLATION_EXCEPTION_SWALLOWED` | Warns about `catch(Exception)` in suspend | 4.2 |

---

### Rule Details

#### 1. No GlobalScope Usage

```kotlin
// ❌ ERROR: GlobalScope usage is not allowed
GlobalScope.launch {
    // This coroutine will run until completion regardless of
    // any other lifecycle considerations
}
```

#### 2. No Inline CoroutineScope Creation

```kotlin
// ❌ ERROR: Inline CoroutineScope creation is not allowed
CoroutineScope(Dispatchers.IO).launch {
    // This coroutine has no parent scope to manage its lifecycle
}
```

#### 3. Structured Scope Required (with Framework Support)

```kotlin
// ❌ ERROR: Unstructured coroutine launch detected
fun processData(scope: CoroutineScope) {
    scope.launch { /* ... */ }  // scope is not marked as structured
}

// ✅ OK: Scope is explicitly marked as structured
fun processData(@StructuredScope scope: CoroutineScope) {
    scope.launch { /* ... */ }  // Allowed - conscious decision
}

// ✅ OK: Framework scopes are automatically recognized
class MyViewModel : ViewModel() {
    fun load() {
        viewModelScope.launch { /* ... */ }  // Lifecycle-aware
    }
}

// ✅ OK: Android LifecycleScope
class MyActivity : AppCompatActivity() {
    fun load() {
        lifecycleScope.launch { /* ... */ }  // Tied to Activity lifecycle
    }
}

// ✅ OK: Compose scope
@Composable
fun MyScreen() {
    val scope = rememberCoroutineScope()
    scope.launch { /* ... */ }  // Tied to composition
}
```

**Recognized Framework Scopes (no annotation needed):**

| Scope | Framework | Lifecycle |
|-------|-----------|-----------|
| `viewModelScope` | Android ViewModel / KMP Common ViewModel | ViewModel cleared |
| `lifecycleScope` | Android LifecycleOwner | Lifecycle destroyed |
| `rememberCoroutineScope()` | Jetpack Compose / Compose Multiplatform | Composition leaves |

#### 4. No runBlocking in Suspend Functions

```kotlin
// ❌ ERROR: runBlocking should not be called inside a suspend function
suspend fun fetchData() {
    runBlocking {  // Blocks the thread, defeats coroutines purpose
        delay(1000)
    }
}

// ✅ OK: runBlocking in regular function (entry point)
fun main() = runBlocking {
    fetchData()
}
```

#### 5. No Job/SupervisorJob in Builders

```kotlin
// ❌ ERROR: Breaks parent-child relationship
scope.launch(Job()) { /* ... */ }
scope.launch(SupervisorJob()) { /* ... */ }
withContext(SupervisorJob()) { /* ... */ }

// ✅ OK: Use supervisorScope for supervisor behavior
suspend fun process() = supervisorScope {
    launch { task1() }
    launch { task2() }
}
```

#### 6. No Extending CancellationException

```kotlin
// ❌ ERROR: Domain errors should not extend CancellationException
class UserNotFoundException : CancellationException("User not found")

// ✅ OK: Use regular Exception
class UserNotFoundException : Exception("User not found")
```

#### 7. Dispatchers.Unconfined Warning

```kotlin
// ⚠️ WARNING: Unpredictable execution thread
scope.launch(Dispatchers.Unconfined) { /* ... */ }

// ✅ OK: Use appropriate dispatchers
scope.launch(Dispatchers.Default) { /* CPU-bound */ }
scope.launch(Dispatchers.IO) { /* IO-bound */ }
```

#### 8. Suspend in Finally Warning

```kotlin
// ⚠️ WARNING: May not execute if cancelled
try { doWork() } finally {
    saveToDb()  // Suspend call without NonCancellable
}

// ✅ OK: Protected with NonCancellable
try { doWork() } finally {
    withContext(NonCancellable) {
        saveToDb()
    }
}
```

#### 9. CancellationException Swallowed Warning

```kotlin
// ⚠️ WARNING: May swallow CancellationException
suspend fun process() {
    try { work() }
    catch (e: Exception) { log(e) }  // Catches CancellationException too!
}

// ✅ OK: Handle CancellationException separately
suspend fun process() {
    try { work() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { log(e) }
}
```

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()  // For local development
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

// build.gradle.kts
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

dependencies {
    implementation("io.github.santimattius:annotations:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

### Kotlin Multiplatform

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.3.0"
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js(IR) { browser(); nodejs() }
    
    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.santimattius:annotations:0.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
    }
}
```

### Supported Platforms

| Platform | Target |
|----------|--------|
| **JVM** | jvm |
| **JavaScript** | js (browser, nodejs) |
| **iOS** | iosArm64, iosX64, iosSimulatorArm64 |
| **macOS** | macosArm64, macosX64 |
| **watchOS** | watchosArm64, watchosX64, watchosSimulatorArm64 |
| **tvOS** | tvosArm64, tvosX64, tvosSimulatorArm64 |
| **Linux** | linuxX64, linuxArm64 |
| **Windows** | mingwX64 |
| **WASM** | wasmJs, wasmWasi |

## 🔧 Usage

### Basic Usage with Function Parameters

```kotlin
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun loadData(@StructuredScope scope: CoroutineScope) {
    scope.launch {
        // Fetch data from network
    }
}
```

### Using Framework Scopes (No Annotation Needed)

```kotlin
// Android ViewModel
class MyViewModel : ViewModel() {
    fun load() {
        viewModelScope.launch {
            // Automatically recognized as structured
        }
    }
}

// Android Activity/Fragment
class MyActivity : AppCompatActivity() {
    fun load() {
        lifecycleScope.launch {
            // Automatically recognized as structured
        }
    }
}

// Jetpack Compose
@Composable
fun MyComposable() {
    val scope = rememberCoroutineScope()
    Button(onClick = { 
        scope.launch { doSomething() }
    }) {
        Text("Click")
    }
}
```

### Constructor Injection

```kotlin
class UserService(
    @property:StructuredScope 
    private val scope: CoroutineScope
) {
    fun fetchUser(id: String) {
        scope.launch {
            // Network call
        }
    }
}
```

### Complete Repository Example

```kotlin
class DataRepository(
    @property:StructuredScope private val scope: CoroutineScope
) {
    fun fetchData() {
        scope.launch {
            try {
                val data = loadFromNetwork()
                saveToCache(data)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }
    
    suspend fun fetchMultiple() = supervisorScope {
        val result1 = async { fetchItem1() }
        val result2 = async { fetchItem2() }
        listOf(result1.await(), result2.await())
    }
}
```

## 📋 Annotation Reference

### @StructuredScope

Marks a CoroutineScope as intentionally structured.

```kotlin
@Target(
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.FIELD
)
@Retention(AnnotationRetention.BINARY)
annotation class StructuredScope
```

**Use-site targets:**

| Syntax | Target | Use Case |
|--------|--------|----------|
| `@StructuredScope val x` | Parameter | Won't work for property access |
| `@property:StructuredScope val x` | Property | ✅ Recommended |
| `@field:StructuredScope val x` | Backing field | Java interop |

## 🏗️ Architecture

```
structured-coroutines/
├── annotations/          # Multiplatform annotations
├── compiler/             # K2/FIR Compiler Plugin (7 checkers)
├── gradle-plugin/        # Gradle Plugin Integration
└── sample/               # Usage examples
```

## 🧪 Testing

```bash
# Publish to Maven Local first
./gradlew publishToMavenLocal

# Run tests
./gradlew :compiler:test
```

## 🛠️ Requirements

- Kotlin 2.3.0 or higher
- K2 compiler (enabled by default in Kotlin 2.3+)
- Gradle 8.0+

## 📄 License

```
Copyright 2024 Santiago Mattiauda
Licensed under the Apache License, Version 2.0
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📚 Related Resources

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Structured Concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)
- [Android ViewModel Guide](https://developer.android.com/topic/libraries/architecture/viewmodel)
- [Compose Side Effects](https://developer.android.com/jetpack/compose/side-effects)
