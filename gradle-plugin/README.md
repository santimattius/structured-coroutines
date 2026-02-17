# Gradle Plugin for Structured Coroutines

Gradle plugin that integrates the Structured Coroutines Kotlin Compiler Plugin for enforcing
structured concurrency best practices.

## Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
- [Profiles (strict / gradual / relaxed)](#profiles-strict--gradual--relaxed)
- [Rules Overview](#rules-overview)
- [Usage Examples](#usage-examples)
- [Kotlin Multiplatform Support](#kotlin-multiplatform-support)
- [Configuration Strategies](#configuration-strategies)
- [Testing i18n (Compiler Messages)](#testing-i18n-compiler-messages)
- [Testing from Another Project](#testing-from-another-project)
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

### Profiles (strict / gradual / relaxed)

You can apply a preset instead of configuring each rule:

```kotlin
structuredCoroutines {
    useStrictProfile()   // Default: 7 error, 4 warning (greenfield)
    // useGradualProfile()  // All rules warning (migration)
    // useRelaxedProfile()   // Same as gradual
}
```

| Profile   | When to use | Effect |
|-----------|--------------|--------|
| **Strict**  | New projects or when you want the build to fail on violations | 7 rules → error, 4 rules → warning (defaults) |
| **Gradual** | Migrating legacy code; build must not fail while you fix issues | All 11 rules → **warning** |
| **Relaxed** | Same as gradual; see findings without blocking the build | All 11 rules → **warning** |

**Severity per rule by profile:**

| Rule                              | Strict | Gradual / Relaxed |
|-----------------------------------|--------|-------------------|
| `globalScopeUsage`                | error  | warning           |
| `inlineCoroutineScope`            | error  | warning           |
| `unstructuredLaunch`              | error  | warning           |
| `runBlockingInSuspend`            | error  | warning           |
| `jobInBuilderContext`             | error  | warning           |
| `cancellationExceptionSubclass`   | error  | warning           |
| `unusedDeferred`                  | error  | warning           |
| `dispatchersUnconfined`           | warning| warning           |
| `suspendInFinally`                | warning| warning           |
| `cancellationExceptionSwallowed`  | warning| warning           |
| `redundantLaunchInCoroutineScope` | warning| warning           |

### Single entry point for rules

This plugin is the **recommended single entry point** for structured-coroutines: it applies the Kotlin compiler plugin and fits into the same project as **Detekt** and **Android Lint**. For consistent behavior across tools, keep severity choices aligned (e.g. use the same error/warning level for a given rule in the plugin and in `detekt.yml` / Lint config). Rule codes and suppression IDs are listed in [docs/rule-codes.yml](../docs/rule-codes.yml) and [docs/RULES_SYNC_COMPARISON.md](../docs/RULES_SYNC_COMPARISON.md).

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

### Using a profile

**New project (strict):** one line applies the default strict behavior.

```kotlin
plugins {
    kotlin("jvm") version "2.3.0"
    id("io.github.santimattius.structured-coroutines") version "0.1.0"
}

structuredCoroutines {
    useStrictProfile()
}
```

**Legacy project (gradual):** all rules as warnings so the build does not fail while you fix issues.

```kotlin
structuredCoroutines {
    useGradualProfile()
}
```

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

## Testing i18n (Compiler Messages)

The Gradle plugin does not show its own messages; the texts you see when a rule is violated (e.g. `[SCOPE_001] GlobalScope usage...`) come from the **compiler plugin**. **The default language is English** so builds are consistent regardless of system locale.

### See messages in Spanish

Set the compiler locale system property so diagnostics use `CompilerBundle_es.properties`:

**Option A – one-off (Unix/macOS):**

```bash
JAVA_TOOL_OPTIONS="-Dstructured.coroutines.compiler.locale=es" ./gradlew compileKotlin
```

**Option B – Gradle JVM args (project-wide):**

In `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Dstructured.coroutines.compiler.locale=es
```

**Trigger a diagnostic:** Use code that triggers a rule, e.g. `GlobalScope.launch { }`. The error message should appear in Spanish (e.g. *"El uso de GlobalScope no está permitido..."*).

**Back to English:** Omit the property or remove `-Dstructured.coroutines.compiler.locale=es`. **Use system locale:** `-Dstructured.coroutines.compiler.locale=default`.

---

## Testing from Another Project

To try the plugin from a **different project** (e.g. another repo or a folder outside this one):

### 1. Publish to Maven Local (from this repo)

In the **structured-coroutines** repo root:

```bash
./gradlew :annotations:publishToMavenLocal :compiler:publishToMavenLocal :gradle-plugin:publishToMavenLocal
```

Use the same **version** as in this project (see `gradle.properties` → `PROJECT_VERSION`). Your other project must use that version in the plugin and dependencies.

### 2. Create a minimal project

In another directory (e.g. `~/my-app` or another repo), create:

**settings.gradle.kts:**

```kotlin
rootProject.name = "my-app"

pluginManagement {
    repositories {
        mavenLocal()   // Must be first so it picks up your published plugin
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
    id("io.github.santimattius.structured-coroutines") version "0.3.1"  // Same as PROJECT_VERSION in this repo
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("io.github.santimattius:structured-coroutines-annotations:0.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
}
```

**src/main/kotlin/Main.kt** (code that triggers an error on purpose):

```kotlin
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main() {
    GlobalScope.launch { println("Hello") }  // Will fail with [SCOPE_001]
}
```

### 3. Run the build

From the **other project** directory:

```bash
./gradlew compileKotlin
```

The build should **fail** with a message like `[SCOPE_001] GlobalScope usage is not allowed...` and a link to the docs. That confirms the plugin and compiler are applied.

### 4. Optional: Spanish messages

In the **other project**, create or edit `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx1024m -Dstructured.coroutines.compiler.locale=es
```

Run `./gradlew compileKotlin` again; the error should appear in Spanish (e.g. *El uso de GlobalScope no está permitido...*).

### 5. Successful build (no errors)

Remove or change the offending code (e.g. use `coroutineScope { launch { ... } }` or inject a `CoroutineScope` with `@StructuredScope`) so that the project compiles successfully.

---

**Automated validation:** The compiler module’s functional tests (which use the Gradle plugin) include i18n checks: they verify that a failing build shows the rule code `[SCOPE_001]` and the localized message (English or Spanish), and that `JAVA_TOOL_OPTIONS=-Dstructured.coroutines.compiler.locale=es` yields the Spanish message. Run `./gradlew :compiler:test` (after `publishToMavenLocal` for the plugin/compiler/annotations).

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

## Sample: Compilation examples per rule

The **sample** project in this repository includes a `compilation` package with one Kotlin file per compiler rule (7 errors, 4 warnings). Use it to verify the plugin in your environment or to see exactly what code triggers each rule. See [sample/compilation/README.md](../sample/src/main/kotlin/io/github/santimattius/structured/sample/compilation/README.md) for the package layout and build commands. With the plugin enabled, the sample fails compilation by design due to the error examples.

---

## License

```
Copyright 2026 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```
