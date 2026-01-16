# Structured Coroutines

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)

A Kotlin Compiler Plugin that enforces **structured concurrency** rules for Kotlin Coroutines, inspired by Swift Concurrency. It detects unsafe coroutine patterns at compile-time, emitting **errors** (not warnings) to prevent common pitfalls.

## 🎯 Purpose

Kotlin Coroutines are powerful but can be misused, leading to:
- **Resource leaks** from orphaned coroutines
- **Uncontrolled lifecycle** with `GlobalScope`
- **Difficult debugging** due to scattered coroutine launches

This plugin enforces that all `launch` and `async` calls happen on **explicitly structured scopes**, making your concurrent code safer, more predictable, and easier to maintain.

## ✨ Features

- 🔍 **Compile-time detection** of unsafe coroutine patterns
- 🚫 **Error-level diagnostics** (not warnings) to enforce compliance
- 🎯 **Opt-in model** via `@StructuredScope` annotation
- 🔧 **K2/FIR compatible** - works with Kotlin 2.3+
- 📦 **Zero runtime overhead** - all checks happen at compile time

## 🚨 Rules Enforced

### 1. No Inline CoroutineScope Creation

Creating a `CoroutineScope` inline and immediately launching a coroutine on it bypasses structured concurrency. The scope has no parent and its lifecycle is uncontrolled.

```kotlin
// ❌ ERROR: Inline CoroutineScope creation is not allowed
CoroutineScope(Dispatchers.IO).launch {
    // This coroutine has no parent scope to manage its lifecycle
}

// ❌ ERROR: Same issue with async
CoroutineScope(Dispatchers.Default).async {
    // Orphaned coroutine
}
```

### 2. No GlobalScope Usage

`GlobalScope` is a singleton that lives for the entire application lifetime. Coroutines launched on it are effectively orphaned and can cause resource leaks.

```kotlin
// ❌ ERROR: GlobalScope usage is not allowed
GlobalScope.launch {
    // This coroutine will run until completion regardless of
    // any other lifecycle considerations
}

// ❌ ERROR: GlobalScope with async
GlobalScope.async {
    // Memory leak potential
}
```

### 3. Structured Scope Required

All coroutine launches must happen on scopes explicitly marked with `@StructuredScope`. This ensures deliberate decisions about coroutine lifecycle.

```kotlin
// ❌ ERROR: Unstructured coroutine launch detected
fun processData(scope: CoroutineScope) {
    scope.launch { /* ... */ }  // scope is not marked as structured
}

// ✅ OK: Scope is explicitly marked as structured
fun processData(@StructuredScope scope: CoroutineScope) {
    scope.launch { /* ... */ }  // Allowed - conscious decision
}
```

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
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
    implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
}
```

### Manual Setup (with -Xplugin)

If you prefer manual setup, add the compiler plugin JAR directly:

```kotlin
// build.gradle.kts
kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xplugin=/path/to/structured-coroutines-compiler.jar")
    }
}

dependencies {
    implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
}
```

## 🔧 Usage

### Basic Usage with Function Parameters

The most common pattern is annotating function parameters:

```kotlin
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class DataLoader {
    
    fun loadData(@StructuredScope scope: CoroutineScope) {
        scope.launch {
            // Fetch data from network
        }
    }
}
```

### Class Properties

For class-level scopes, annotate the property:

```kotlin
class Repository {
    
    @StructuredScope
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun fetchData() {
        scope.launch {
            // This is allowed
        }
    }
    
    fun cleanup() {
        scope.cancel()  // Don't forget to cancel!
    }
}
```

### Constructor Injection (Recommended)

For dependency injection, use `@property:StructuredScope` to ensure the annotation applies to the property:

```kotlin
class UserService(
    @property:StructuredScope 
    private val ioScope: CoroutineScope
) {
    
    fun fetchUser(id: String) {
        ioScope.launch {
            // Network call
        }
    }
    
    suspend fun fetchUserAsync(id: String) = ioScope.async {
        // Return user data
    }
}

// Usage with DI
class AppModule {
    @Provides
    @StructuredScope
    fun provideIoScope(): CoroutineScope = 
        CoroutineScope(Dispatchers.IO + SupervisorJob())
}
```

### ViewModel Pattern (Android)

```kotlin
class MainViewModel(
    @property:StructuredScope 
    private val viewModelScope: CoroutineScope
) : ViewModel() {
    
    fun loadContent() {
        viewModelScope.launch {
            // Safe - tied to ViewModel lifecycle
        }
    }
}

// Or using AndroidX viewModelScope
class MainViewModel : ViewModel() {
    
    @StructuredScope
    private val scope: CoroutineScope
        get() = viewModelScope  // AndroidX provides this
    
    fun loadContent() {
        scope.launch { /* ... */ }
    }
}
```

## 📋 Annotation Reference

### `@StructuredScope`

Marks a `CoroutineScope` as intentionally structured, allowing `launch` and `async` calls on it.

```kotlin
@Target(
    AnnotationTarget.VALUE_PARAMETER,  // Function parameters
    AnnotationTarget.PROPERTY,          // Class properties
    AnnotationTarget.FIELD              // Java fields
)
@Retention(AnnotationRetention.BINARY)
annotation class StructuredScope
```

**Use-site targets for constructor properties:**

| Syntax | Target | Use Case |
|--------|--------|----------|
| `@StructuredScope val x` | Parameter (default) | Won't work for property access |
| `@property:StructuredScope val x` | Property | ✅ Recommended for constructor vals |
| `@field:StructuredScope val x` | Backing field | Java interop |

## 🏗️ Architecture

The project follows a clean separation of concerns:

```
structured-coroutines/
├── annotations/     # Public annotations (no dependencies)
│   └── @StructuredScope
│
├── compiler/        # K2/FIR Compiler Plugin
│   ├── CompilerPluginRegistrar
│   ├── FirExtensionRegistrar
│   ├── FirAdditionalCheckersExtension
│   └── FirFunctionCallChecker (rule implementation)
│
├── gradle-plugin/   # Gradle Plugin (no compiler deps)
│   └── KotlinCompilerPluginSupportPlugin
│
└── sample/          # Usage examples
```

### Key Components

- **UnstructuredLaunchChecker**: FIR call checker that analyzes `launch`/`async` calls
- **StructuredCoroutinesErrors**: Diagnostic factory definitions
- **ScoroutinesCallCheckerExtension**: Registers checkers with the FIR pipeline

## 🔬 How It Works

1. **Detection**: The plugin intercepts all function calls during FIR analysis
2. **Filtering**: Identifies calls to `launch` or `async` from `kotlinx.coroutines`
3. **Receiver Analysis**: Examines the receiver (the scope being called on)
4. **Validation**: Checks if the receiver:
   - Is NOT `GlobalScope`
   - Is NOT an inline `CoroutineScope(...)` creation
   - Has `@StructuredScope` annotation on its declaration
5. **Reporting**: Emits compilation errors for violations

## 🆚 Comparison with Other Approaches

| Approach | When | Type Safety | CI Friendly |
|----------|------|-------------|-------------|
| **This Plugin** | Compile-time | ✅ Full | ✅ Yes |
| Detekt/Lint | Static analysis | ⚠️ Partial | ✅ Yes |
| Code Review | Manual | ❌ None | ❌ No |
| Runtime checks | Run-time | ❌ Late | ❌ No |

## 🛠️ Requirements

- Kotlin 2.3.0 or higher
- K2 compiler (enabled by default in Kotlin 2.3+)
- Gradle 8.0+ (for Gradle plugin)

## 📄 License

```
Copyright 2024 Santiago Mattiauda

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📚 Related Resources

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Structured Concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)
- [Swift Concurrency](https://docs.swift.org/swift-book/LanguageGuide/Concurrency.html) (inspiration)
- [K2 Compiler Migration Guide](https://kotlinlang.org/docs/k2-compiler-migration-guide.html)
