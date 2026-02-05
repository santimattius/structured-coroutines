# Gradle Plugin for Structured Coroutines

Gradle plugin that integrates the Structured Coroutines Kotlin Compiler Plugin for enforcing
structured concurrency best practices.

## Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
- [Rules Overview](#rules-overview)
- [Usage Examples](#usage-examples)
- [Kotlin Multiplatform Support](#kotlin-multiplatform-support)
- [Configuration Strategies](#configuration-strategies)
- [Troubleshooting](#troubleshooting)
- [Quick Start Templates](#quick-start-templates)

---

## Installation

### 1. Publish to Maven Local (Development)

From the plugin project directory:

```bash
./gradlew publishToMavenLocal
```

This publishes artifacts to `~/.m2/repository/`:

| Artifact                                                              | Purpose                                          |
|-----------------------------------------------------------------------|--------------------------------------------------|
| `io.github.santimattius:structured-coroutines-annotations:0.1.0`      | Annotations (`@StructuredScope`) - Multiplatform |
| `io.github.santimattius:structured-coroutines-compiler:0.1.0`         | Kotlin Compiler Plugin                           |
| `io.github.santimattius.structured-coroutines:...gradle.plugin:0.1.0` | Gradle Plugin                                    |

### 2. Configure Repositories

**settings.gradle.kts:**

```kotlin
pluginManagement {
    repositories {
        mavenLocal()  // Add first for local development
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()  // Add first for local development
        mavenCentral()
        google()
    }
}
```

### 3. Apply the Plugin

**build.gradle.kts:**

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"  // Requires Kotlin 2.0+
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

dependencies {
    // Annotations for @StructuredScope
    implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")

    // Coroutines dependency
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

---

## Configuration

### Extension DSL

Configure rule severity using the `structuredCoroutines` extension:

```kotlin
structuredCoroutines {
    // Error rules (block compilation by default)
    globalScopeUsage.set("error")              // Default: "error"
    inlineCoroutineScope.set("error")          // Default: "error"
    unstructuredLaunch.set("error")            // Default: "error"
    runBlockingInSuspend.set("error")          // Default: "error"
    jobInBuilderContext.set("error")           // Default: "error"
    cancellationExceptionSubclass.set("error") // Default: "error"
    unusedDeferred.set("error")                // Default: "error"

    // Warning rules (allow compilation by default)
    dispatchersUnconfined.set("warning")               // Default: "warning"
    suspendInFinally.set("warning")                    // Default: "warning"
    cancellationExceptionSwallowed.set("warning")      // Default: "warning"
    redundantLaunchInCoroutineScope.set("warning")     // Default: "warning"
}
```

### Severity Levels

| Level       | Behavior                                     |
|-------------|----------------------------------------------|
| `"error"`   | Reports as compilation error (blocks build)  |
| `"warning"` | Reports as warning (allows build to succeed) |

---

## Rules Overview

### Summary Table

| Rule                              | Default Severity | Description                                         |
|-----------------------------------|------------------|-----------------------------------------------------|
| `globalScopeUsage`                | Error            | Detects `GlobalScope.launch/async`                  |
| `inlineCoroutineScope`            | Error            | Detects `CoroutineScope(...).launch/async`          |
| `unstructuredLaunch`              | Error            | Detects launch on non-annotated scopes              |
| `runBlockingInSuspend`            | Error            | Detects `runBlocking` in suspend functions          |
| `jobInBuilderContext`             | Error            | Detects `Job()`/`SupervisorJob()` in builders       |
| `cancellationExceptionSubclass`   | Error            | Detects classes extending `CancellationException`   |
| `unusedDeferred`                  | Error            | Detects `async` without `await()`                   |
| `dispatchersUnconfined`           | Warning          | Detects `Dispatchers.Unconfined` usage              |
| `suspendInFinally`                | Warning          | Detects suspend in finally without `NonCancellable` |
| `cancellationExceptionSwallowed`  | Warning          | Detects `catch(Exception)` swallowing cancellation  |
| `redundantLaunchInCoroutineScope` | Warning          | Detects redundant launch in `coroutineScope`        |

### Rules Count

| Severity          | Count  |
|-------------------|--------|
| Error (default)   | 7      |
| Warning (default) | 4      |
| **Total**         | **11** |

---

## Usage Examples

### Using @StructuredScope

```kotlin
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.*

// ✅ GOOD: Parameter annotated with @StructuredScope
fun loadData(@StructuredScope scope: CoroutineScope) {
    scope.launch {
        fetchData()
    }
}

// ✅ GOOD: Property annotated with @StructuredScope
class DataRepository(
    @StructuredScope private val scope: CoroutineScope
) {
    fun refresh() {
        scope.launch {
            loadFromNetwork()
        }
    }
}

// ✅ GOOD: Using structured concurrency patterns
suspend fun processAll() = coroutineScope {
    launch { task1() }
    launch { task2() }
}
```

### What Gets Flagged as Errors

```kotlin
// ❌ ERROR: GlobalScope usage
GlobalScope.launch { }

// ❌ ERROR: Inline CoroutineScope creation
CoroutineScope(Dispatchers.IO).launch { }

// ❌ ERROR: Unstructured launch (scope not annotated)
fun bad(scope: CoroutineScope) {
    scope.launch { }  // scope is not @StructuredScope
}

// ❌ ERROR: runBlocking in suspend function
suspend fun bad() {
    runBlocking { }
}

// ❌ ERROR: Job/SupervisorJob in builder
launch(Job()) { }
launch(SupervisorJob()) { }

// ❌ ERROR: Extending CancellationException
class MyError : CancellationException()

// ❌ ERROR: async without await
val deferred = scope.async { compute() }
// deferred never used
```

### What Gets Flagged as Warnings

```kotlin
// ⚠️ WARNING: Dispatchers.Unconfined
launch(Dispatchers.Unconfined) { }

// ⚠️ WARNING: Suspend in finally without NonCancellable
try {
} finally {
    suspendFunction()  // Should be wrapped in withContext(NonCancellable)
}

// ⚠️ WARNING: catch(Exception) may swallow CancellationException
suspend fun process() {
    try {
        work()
    } catch (e: Exception) {
        log(e)
    }  // Should handle CancellationException
}

// ⚠️ WARNING: Redundant launch
suspend fun bad() = coroutineScope {
    launch { work() }  // Unnecessary wrapper
}
```

---

## Kotlin Multiplatform Support

The plugin fully supports Kotlin Multiplatform projects.

### Supported Targets

| Platform | Artifacts                                               |
|----------|---------------------------------------------------------|
| JVM      | `annotations-jvm`                                       |
| JS       | `annotations-js`                                        |
| iOS      | `annotations-iosarm64`, `annotations-iossimulatorarm64` |
| macOS    | `annotations-macosx64`, `annotations-macosarm64`        |
| watchOS  | `annotations-watchosarm64`, etc.                        |
| tvOS     | `annotations-tvosarm64`, etc.                           |
| Linux    | `annotations-linuxx64`, `annotations-linuxarm64`        |
| Windows  | `annotations-mingwx64`                                  |
| WASM     | `annotations-wasmjs`, `annotations-wasmwasi`            |

### KMP Configuration

**build.gradle.kts:**

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.0"
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()
    js(IR) {
        browser()
        nodejs()
    }

    sourceSets {
        commonMain {
            dependencies {
                // Multiplatform annotations
                implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
    }
}

structuredCoroutines {
    globalScopeUsage.set("error")
    unstructuredLaunch.set("error")
}
```

### KMP Usage Example

```kotlin
// commonMain/kotlin/MyRepository.kt
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.*

class DataRepository(
    @StructuredScope private val scope: CoroutineScope
) {
    fun refresh() {
        // ✅ Works on all platforms: JVM, iOS, JS, etc.
        scope.launch {
            fetchData()
        }
    }

    private suspend fun fetchData() {
        // Platform-agnostic suspend function
    }
}
```

---

## Configuration Strategies

### Gradual Migration

For existing projects, start with warnings and gradually move to errors:

```kotlin
structuredCoroutines {
    // Start with warnings to identify issues
    globalScopeUsage.set("warning")
    unstructuredLaunch.set("warning")
    inlineCoroutineScope.set("warning")

    // Keep critical rules as errors
    runBlockingInSuspend.set("error")
    jobInBuilderContext.set("error")
}
```

### Strict Enforcement

For new projects, enforce all rules strictly:

```kotlin
structuredCoroutines {
    // All rules as errors
    globalScopeUsage.set("error")
    unstructuredLaunch.set("error")
    inlineCoroutineScope.set("error")
    runBlockingInSuspend.set("error")
    jobInBuilderContext.set("error")
    cancellationExceptionSubclass.set("error")
    unusedDeferred.set("error")

    // Even warnings become errors
    dispatchersUnconfined.set("error")
    suspendInFinally.set("error")
    cancellationExceptionSwallowed.set("error")
    redundantLaunchInCoroutineScope.set("error")
}
```

### Relaxed Mode

For projects wanting guidance without strict enforcement:

```kotlin
structuredCoroutines {
    // All rules as warnings
    globalScopeUsage.set("warning")
    unstructuredLaunch.set("warning")
    inlineCoroutineScope.set("warning")
    runBlockingInSuspend.set("warning")
    jobInBuilderContext.set("warning")
    cancellationExceptionSubclass.set("warning")
    unusedDeferred.set("warning")
    dispatchersUnconfined.set("warning")
    suspendInFinally.set("warning")
    cancellationExceptionSwallowed.set("warning")
    redundantLaunchInCoroutineScope.set("warning")
}
```

---

## Troubleshooting

### Plugin Not Found

| Issue                     | Solution                                                                                                     |
|---------------------------|--------------------------------------------------------------------------------------------------------------|
| Plugin not found          | Run `publishToMavenLocal` in the plugin project                                                              |
| Repository not configured | Add `mavenLocal()` to both `pluginManagement.repositories` and `dependencyResolutionManagement.repositories` |
| Wrong Kotlin version      | Requires Kotlin 2.0+ (K2 compiler)                                                                           |

### Configuration Not Working

| Issue                | Solution                                                                 |
|----------------------|--------------------------------------------------------------------------|
| Severity not applied | Use `set("error")` or `set("warning")` syntax                            |
| Stale configuration  | Run `./gradlew clean build`                                              |
| Wrong file           | Ensure `structuredCoroutines` block is in the correct `build.gradle.kts` |

### Annotations Not Found

```kotlin
// For JVM projects
implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")

// For KMP projects (in commonMain)
implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
```

### Verify Maven Local

```bash
ls ~/.m2/repository/io/github/santimattius/
```

Expected directories:

- `annotations/`
- `structured-coroutines-compiler/`
- `structured-coroutines/` (plugin marker)

---

## Quick Start Templates

### JVM Project

**settings.gradle.kts:**

```kotlin
rootProject.name = "my-coroutines-project"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}
```

**build.gradle.kts:**

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

dependencies {
    implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}

kotlin {
    jvmToolchain(17)
}
```

**src/main/kotlin/Main.kt:**

```kotlin
import io.github.santimattius.structured.annotations.StructuredScope
import kotlinx.coroutines.*

fun main() = runBlocking {
    val processor = DataProcessor(this)
    processor.process()
}

class DataProcessor(@StructuredScope private val scope: CoroutineScope) {
    fun process() {
        scope.launch {
            println("Processing...")
            delay(100)
            println("Done!")
        }
    }
}
```

### KMP Project

**settings.gradle.kts:**

```kotlin
rootProject.name = "my-kmp-project"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        google()
    }
}
```

**build.gradle.kts:**

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.0"
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

kotlin {
    jvm()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain {
            dependencies {
                implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            }
        }
    }
}

structuredCoroutines {
    globalScopeUsage.set("error")
    unstructuredLaunch.set("error")
}
```

---

## Build Output Examples

### Error Output

```
e: MyFile.kt:10:5: GlobalScope usage is not allowed. GlobalScope bypasses
   structured concurrency and can lead to resource leaks. Use a CoroutineScope
   annotated with @StructuredScope instead.
```

### Warning Output

```
w: MyFile.kt:15:9: Dispatchers.Unconfined usage detected. Dispatchers.Unconfined
   runs coroutines on the calling thread, which can lead to unpredictable behavior.
   Consider using Dispatchers.Default or Dispatchers.IO instead.
```

---

## License

```
Copyright 2026 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```
