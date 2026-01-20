# Structured Coroutines

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-Supported-orange.svg)](https://kotlinlang.org/docs/multiplatform.html)

A comprehensive toolkit for enforcing **structured concurrency** in Kotlin Coroutines, inspired by Swift Concurrency. It provides multiple layers of protection through compile-time checks and static analysis.

## 🎯 Purpose

Kotlin Coroutines are powerful but can be misused, leading to:
- **Resource leaks** from orphaned coroutines
- **Uncontrolled lifecycle** with `GlobalScope`
- **Difficult debugging** due to scattered coroutine launches
- **Deadlocks** from `runBlocking` in suspend functions
- **Broken cancellation** from swallowed `CancellationException`
- **Thread starvation** from blocking calls in coroutines

This toolkit enforces structured concurrency best practices through:
1. **Compiler Plugin** - Errors at compile time (K2/FIR)
2. **Detekt Rules** - Static analysis warnings

---

## 📦 Toolkit Components

| Module | Purpose | When |
|--------|---------|------|
| `compiler` | K2/FIR Compiler Plugin | Compile-time errors |
| `detekt-rules` | Detekt custom rules | Static analysis |
| `annotations` | `@StructuredScope` annotation | Runtime/Compile |
| `gradle-plugin` | Gradle integration | Build configuration |

---

## ✨ Features

- 🔍 **Compile-time detection** of unsafe coroutine patterns
- 🚫 **Error-level diagnostics** for critical violations
- ⚠️ **Warning-level diagnostics** for code smells
- 🎯 **Opt-in model** via `@StructuredScope` annotation
- 🤖 **Framework-aware** - recognizes `viewModelScope`, `lifecycleScope`, `rememberCoroutineScope()`
- 🔧 **K2/FIR compatible** - works with Kotlin 2.3+
- 📦 **Zero runtime overhead** - all checks happen at compile time
- 🌍 **Kotlin Multiplatform** - supports JVM, JS, Native, WASM
- 🔎 **Detekt integration** - additional static analysis rules

---

## 🚨 Rules Overview

### Compiler Plugin (Compile-time)

#### Errors (Block Compilation)

| Rule | Description |
|------|-------------|
| `GLOBAL_SCOPE_USAGE` | Prohibits `GlobalScope.launch/async` |
| `INLINE_COROUTINE_SCOPE` | Prohibits `CoroutineScope(Dispatchers.X).launch` |
| `UNSTRUCTURED_COROUTINE_LAUNCH` | Requires structured scope |
| `RUN_BLOCKING_IN_SUSPEND` | Prohibits `runBlocking` in suspend functions |
| `JOB_IN_BUILDER_CONTEXT` | Prohibits `Job()`/`SupervisorJob()` in builders |
| `CANCELLATION_EXCEPTION_SUBCLASS` | Prohibits extending `CancellationException` |

#### Warnings (Allow Compilation)

| Rule | Description |
|------|-------------|
| `DISPATCHERS_UNCONFINED_USAGE` | Warns about `Dispatchers.Unconfined` |
| `SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE` | Warns about unprotected suspend in finally |
| `CANCELLATION_EXCEPTION_SWALLOWED` | Warns about `catch(Exception)` in suspend |

### Detekt Rules (Static Analysis)

#### Compiler Plugin Rules 

| Rule | Description |
|------|-------------|
| `GlobalScopeUsage` | Detects `GlobalScope.launch/async` |
| `InlineCoroutineScope` | Detects `CoroutineScope(...).launch/async` and property initialization |
| `RunBlockingInSuspend` | Detects `runBlocking` in suspend functions |
| `DispatchersUnconfined` | Detects `Dispatchers.Unconfined` usage |
| `CancellationExceptionSubclass` | Detects classes extending `CancellationException` |

#### Detekt-Only Rules

| Rule | Description |
|------|-------------|
| `BlockingCallInCoroutine` | Detects `Thread.sleep`, JDBC, sync HTTP in coroutines |
| `RunBlockingWithDelayInTest` | Detects `runBlocking` + `delay` in tests |
| `ExternalScopeLaunch` | Detects launch on external scope from suspend |
| `LoopWithoutYield` | Detects loops without cooperation points |

**Total: 9 Detekt Rules** (5 from Compiler Plugin + 4 Detekt-only)

---

## 📦 Installation

### Compiler Plugin

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
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

### Detekt Rules

```kotlin
// build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}

dependencies {
    detektPlugins("io.github.santimattius:structured-coroutines-detekt-rules:0.1.0")
}
```

### Kotlin Multiplatform

```kotlin
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

---

## 🔧 Usage

### Using @StructuredScope Annotation

```kotlin
import io.github.santimattius.structured.annotations.StructuredScope

// Function parameter
fun loadData(@StructuredScope scope: CoroutineScope) {
    scope.launch { fetchData() }
}

// Constructor injection
class UserService(
    @property:StructuredScope 
    private val scope: CoroutineScope
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

### Framework Scopes (Auto-recognized)

The plugin automatically recognizes lifecycle-aware framework scopes:

```kotlin
// ✅ Android ViewModel - No annotation needed
class MyViewModel : ViewModel() {
    fun load() {
        viewModelScope.launch { fetchData() }
    }
}

// ✅ Android Activity/Fragment - No annotation needed
class MyActivity : AppCompatActivity() {
    fun load() {
        lifecycleScope.launch { fetchData() }
    }
}

// ✅ Jetpack Compose - No annotation needed
@Composable
fun MyScreen() {
    val scope = rememberCoroutineScope()
    Button(onClick = { scope.launch { doWork() } }) {
        Text("Click")
    }
}
```

**Recognized Framework Scopes:**

| Scope | Framework | Package |
|-------|-----------|---------|
| `viewModelScope` | Android ViewModel | `androidx.lifecycle` |
| `lifecycleScope` | Android Lifecycle | `androidx.lifecycle` |
| `rememberCoroutineScope()` | Jetpack Compose | `androidx.compose.runtime` |

---

## 📋 Rule Details

### 1. No GlobalScope

```kotlin
// ❌ ERROR
GlobalScope.launch { work() }

// ✅ Use framework scopes or @StructuredScope
viewModelScope.launch { work() }
```

### 2. No Inline CoroutineScope

```kotlin
// ❌ ERROR
CoroutineScope(Dispatchers.IO).launch { work() }

// ✅ Use a managed scope
class MyClass(@StructuredScope private val scope: CoroutineScope) {
    fun doWork() = scope.launch { work() }
}
```

### 3. No runBlocking in Suspend

```kotlin
// ❌ ERROR
suspend fun bad() {
    runBlocking { delay(1000) }
}

// ✅ Just suspend
suspend fun good() {
    delay(1000)
}
```

### 4. No Job/SupervisorJob in Builders

```kotlin
// ❌ ERROR
scope.launch(Job()) { work() }
scope.launch(SupervisorJob()) { work() }

// ✅ Use supervisorScope
suspend fun process() = supervisorScope {
    launch { task1() }
    launch { task2() }
}
```

### 5. No Extending CancellationException

```kotlin
// ❌ ERROR
class MyError : CancellationException()

// ✅ Use regular Exception
class MyError : Exception()
```

### 6. Handle CancellationException

```kotlin
// ⚠️ WARNING - May swallow cancellation
suspend fun bad() {
    try { work() }
    catch (e: Exception) { log(e) }
}

// ✅ Handle separately
suspend fun good() {
    try { work() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { log(e) }
}
```

### 7. NonCancellable in Finally

```kotlin
// ⚠️ WARNING - May not execute
try { work() } finally {
    saveToDb()  // Suspend call
}

// ✅ Protected
try { work() } finally {
    withContext(NonCancellable) {
        saveToDb()
    }
}
```

### 8. No Blocking Calls (Detekt)

```kotlin
// ⚠️ Detekt WARNING
scope.launch {
    Thread.sleep(1000)      // Blocking!
    inputStream.read()       // Blocking I/O!
    jdbcStatement.execute()  // Blocking JDBC!
}

// ✅ Use non-blocking alternatives
scope.launch {
    delay(1000)
    withContext(Dispatchers.IO) {
        inputStream.read()
    }
}
```

### 9. Use runTest in Tests (Detekt)

```kotlin
// ⚠️ Detekt WARNING - Slow test
@Test
fun test() = runBlocking {
    delay(1000)  // Real delay!
}

// ✅ Fast test with virtual time
@Test
fun test() = runTest {
    delay(1000)  // Instant!
}
```

### 10. Cooperation Points in Loops (Detekt)

```kotlin
// ⚠️ Detekt WARNING - Can't cancel
suspend fun process(items: List<Item>) {
    for (item in items) {
        heavyWork(item)  // No cooperation point
    }
}

// ✅ Can be cancelled
suspend fun process(items: List<Item>) {
    for (item in items) {
        ensureActive()
        heavyWork(item)
    }
}
```

---

## ⚙️ Configuration

### Detekt (detekt.yml)

```yaml
structured-coroutines:
  # Compiler Plugin Rules 
  GlobalScopeUsage:
    active: true
    severity: error
  InlineCoroutineScope:
    active: true
    severity: error
  RunBlockingInSuspend:
    active: true
    severity: warning
  DispatchersUnconfined:
    active: true
    severity: warning
  CancellationExceptionSubclass:
    active: true
    severity: error
  
  # Detekt-Only Rules
  BlockingCallInCoroutine:
    active: true
    excludes: ['commonMain', 'iosMain']  # JVM-only
  RunBlockingWithDelayInTest:
    active: true
  ExternalScopeLaunch:
    active: true
  LoopWithoutYield:
    active: true
```

**📖 Ver documentación completa:** [Detekt Rules Documentation](./docs-local/DETEKT_RULES.md)

---

## 🏗️ Architecture

```
structured-coroutines/
├── annotations/          # @StructuredScope (Multiplatform)
├── compiler/             # K2/FIR Compiler Plugin
│   ├── UnstructuredLaunchChecker
│   ├── RunBlockingInSuspendChecker
│   ├── JobInBuilderContextChecker
│   ├── DispatchersUnconfinedChecker
│   ├── CancellationExceptionSubclassChecker
│   ├── SuspendInFinallyChecker
│   └── CancellationExceptionSwallowedChecker
├── detekt-rules/         # Detekt Custom Rules
│   ├── GlobalScopeUsageRule 
│   ├── InlineCoroutineScopeRule 
│   ├── RunBlockingInSuspendRule 
│   ├── DispatchersUnconfinedRule 
│   ├── CancellationExceptionSubclassRule 
│   ├── BlockingCallInCoroutineRule
│   ├── RunBlockingWithDelayInTestRule
│   ├── ExternalScopeLaunchRule
│   └── LoopWithoutYieldRule
├── gradle-plugin/        # Gradle Integration
└── sample/               # Examples
```

---

## 🌍 Supported Platforms

| Platform | Compiler Plugin | Detekt Rules |
|----------|-----------------|--------------|
| JVM | ✅ | ✅ |
| Android | ✅ | ✅ |
| iOS | ✅ | ✅ |
| macOS | ✅ | ✅ |
| watchOS | ✅ | ✅ |
| tvOS | ✅ | ✅ |
| Linux | ✅ | ✅ |
| Windows | ✅ | ✅ |
| JS | ✅ | ✅ |
| WASM | ✅ | ✅ |

---

## 🧪 Testing

```bash
# Publish locally
./gradlew publishToMavenLocal

# Run compiler plugin tests
./gradlew :compiler:test

# Run detekt rules tests
./gradlew :detekt-rules:test

# Run all tests
./gradlew test
```

---

## 🆚 Comparison

| Approach | When | Errors | Warnings | CI |
|----------|------|--------|----------|-----|
| **Compiler Plugin** | Compile | ✅ 6 rules | ✅ 3 rules | ✅ |
| **Detekt Rules** | Analysis | ✅ 3 rules | ✅ 6 rules | ✅ |
| **Combined** | Both | ✅ 6 rules | ✅ 9 rules | ✅ |
| Code Review | Manual | ❌ | ❌ | ❌ |
| Runtime | Late | ❌ | ❌ | ❌ |

**Nota:** Detekt Rules incluye 5 reglas del Compiler Plugin  + 4 reglas Detekt-only = **9 reglas totales**

---

## 🛠️ Requirements

- Kotlin 2.3.0+
- K2 compiler (default in Kotlin 2.3+)
- Gradle 8.0+
- Detekt 1.23+ (for detekt-rules)

---

## 📄 License

```
Copyright 2024 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```

---

## 🤝 Contributing

Contributions welcome! Please submit a Pull Request.

```bash
git clone https://github.com/santimattius/structured-coroutines.git
cd structured-coroutines
./gradlew publishToMavenLocal
./gradlew test
```

---

## 📚 Resources

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Structured Concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)
- [Detekt Documentation](https://detekt.dev/)
- [K2 Compiler Guide](https://kotlinlang.org/docs/k2-compiler-migration-guide.html)
- [Detekt Rules Documentation](./docs-local/DETEKT_RULES.md) - Guía completa de uso de Detekt Rules