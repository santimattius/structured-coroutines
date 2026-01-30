# Android Lint Rules for Structured Coroutines

Custom Android Lint rules for enforcing structured concurrency best practices in Kotlin Coroutines with Android-specific detection capabilities.

## Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
- [Rules Overview](#rules-overview)
- [Compiler Plugin Rules](#compiler-plugin-rules)
- [Android-Specific Rules](#android-specific-rules)
- [Additional Rules](#additional-rules)
- [Running Lint](#running-lint)
- [CI Integration](#ci-integration)
- [Suppressing Rules](#suppressing-rules)
- [Comparison with Other Tools](#comparison-with-other-tools)
- [Usage Strategy](#usage-strategy)
- [Limitations](#limitations)

---

## Installation

### 1. Android Lint Setup

Android Lint is included with the Android Gradle Plugin. Ensure the plugin is configured:

```kotlin
// build.gradle.kts (project level)
plugins {
    id("com.android.application") version "8.2.0" apply false
    // or
    id("com.android.library") version "8.2.0" apply false
}
```

### 2. Add Custom Rules Dependency

```kotlin
// build.gradle.kts (Android module)
dependencies {
    lintChecks("io.github.santimattius:structured-coroutines-lint-rules:0.1.0")
}
```

### 3. Local Development Setup

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()  // For local version
        google()
        mavenCentral()
    }
}
```

Publish locally:

```bash
./gradlew :lint-rules:lintJar :lint-rules:publishToMavenLocal
```

---

## Configuration

Configure Android Lint rules in `lint.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<lint>
    <!-- Compiler Plugin Rules -->
    <issue id="GlobalScopeUsage" severity="error" />
    <issue id="InlineCoroutineScope" severity="error" />
    <issue id="RunBlockingInSuspend" severity="error" />
    <issue id="DispatchersUnconfined" severity="warning" />
    <issue id="CancellationExceptionSubclass" severity="error" />
    <issue id="JobInBuilderContext" severity="error" />
    <issue id="SuspendInFinally" severity="warning" />
    <issue id="CancellationExceptionSwallowed" severity="warning" />
    <issue id="AsyncWithoutAwait" severity="error" />

    <!-- Android-Specific Rules -->
    <issue id="MainDispatcherMisuse" severity="error" />
    <issue id="ViewModelScopeLeak" severity="error" />
    <issue id="LifecycleAwareScope" severity="error" />

    <!-- Additional Rules -->
    <issue id="UnstructuredLaunch" severity="error" />
    <issue id="RedundantLaunchInCoroutineScope" severity="warning" />
    <issue id="RunBlockingWithDelayInTest" severity="warning" />
    <issue id="LoopWithoutYield" severity="warning" />
    <issue id="ScopeReuseAfterCancel" severity="warning" />
</lint>
```

---

## Rules Overview

### Summary Table

| Rule | Category | Severity | Description |
|------|----------|----------|-------------|
| `GlobalScopeUsage` | Compiler Plugin | Error | Detects `GlobalScope.launch/async` |
| `InlineCoroutineScope` | Compiler Plugin | Error | Detects `CoroutineScope(...).launch/async` |
| `RunBlockingInSuspend` | Compiler Plugin | Error | Detects `runBlocking` in suspend functions |
| `DispatchersUnconfined` | Compiler Plugin | Warning | Detects `Dispatchers.Unconfined` usage |
| `CancellationExceptionSubclass` | Compiler Plugin | Error | Detects classes extending `CancellationException` |
| `JobInBuilderContext` | Compiler Plugin | Error | Detects `Job()`/`SupervisorJob()` in builders |
| `SuspendInFinally` | Compiler Plugin | Warning | Detects suspend calls in finally without `NonCancellable` |
| `CancellationExceptionSwallowed` | Compiler Plugin | Warning | Detects `catch(Exception)` swallowing cancellation |
| `AsyncWithoutAwait` | Compiler Plugin | Error | Detects `async` without `await()` |
| `MainDispatcherMisuse` | Android-Specific | Error | Detects blocking calls on `Dispatchers.Main` |
| `ViewModelScopeLeak` | Android-Specific | Error | Detects custom scopes in ViewModels |
| `LifecycleAwareScope` | Android-Specific | Error | Validates `lifecycleScope` usage |
| `UnstructuredLaunch` | Additional | Error | Detects launch without structured scope |
| `RedundantLaunchInCoroutineScope` | Additional | Warning | Detects redundant launch in `coroutineScope` |
| `RunBlockingWithDelayInTest` | Additional | Warning | Detects `runBlocking` + `delay` in tests |
| `LoopWithoutYield` | Additional | Warning | Detects loops without cooperation points |
| `ScopeReuseAfterCancel` | Additional | Warning | Detects cancelled scope reuse |

### Rules Count by Category

| Category | Count |
|----------|-------|
| Compiler Plugin Rules | 9 |
| Android-Specific Rules | 3 |
| Additional Rules | 5 |
| **Total** | **17** |

---

## Compiler Plugin Rules

These rules replicate the Compiler Plugin functionality as Android Lint rules, allowing you to use only Lint without the compiler plugin.

### 1. GlobalScopeUsage

**Detects:** Usage of `GlobalScope.launch` or `GlobalScope.async`.

```kotlin
// ❌ BAD
GlobalScope.launch {
    fetchData()  // Coroutine without lifecycle
}

GlobalScope.async {
    computeValue()  // Cannot be cancelled from outside
}

// ✅ GOOD - Framework scopes
class MyViewModel : ViewModel() {
    fun load() {
        viewModelScope.launch { fetchData() }
    }
}

// ✅ GOOD - Structured builders
suspend fun process() = coroutineScope {
    launch { fetchData() }
}
```

**Severity:** Error (configurable)

---

### 2. InlineCoroutineScope

**Detects:** Inline creation of `CoroutineScope(...).launch/async` or properties initialized with `CoroutineScope(...)`.

```kotlin
// ❌ BAD - Inline creation with launch/async
CoroutineScope(Dispatchers.IO).launch {
    fetchData()  // Orphan coroutine
}

// ❌ BAD - Property initialized with CoroutineScope
val viewModelScope = CoroutineScope(Dispatchers.Main)

// ✅ GOOD - Framework scopes
class MyViewModel : ViewModel() {
    fun load() {
        viewModelScope.launch { fetchData() }
    }
}

// ✅ GOOD - Structured builders
suspend fun process() = coroutineScope {
    launch { fetchData() }
}
```

**Severity:** Error (configurable)

---

### 3. RunBlockingInSuspend

**Detects:** Calls to `runBlocking` inside `suspend` functions.

```kotlin
// ❌ BAD - runBlocking in suspend function
suspend fun fetchData() {
    runBlocking {  // Blocks the thread!
        delay(1000)
        loadFromNetwork()
    }
}

// ✅ GOOD - Direct suspend
suspend fun fetchData() {
    delay(1000)  // Non-blocking
    loadFromNetwork()
}

// ✅ GOOD - runBlocking at top level (entry point)
fun main() = runBlocking {
    fetchData()
}
```

**Severity:** Error (configurable)

---

### 4. DispatchersUnconfined

**Detects:** Usage of `Dispatchers.Unconfined` in coroutine builders.

```kotlin
// ⚠️ WARNING - Dispatchers.Unconfined
scope.launch(Dispatchers.Unconfined) {
    doWork()  // Unpredictable thread
}

// ✅ GOOD - Appropriate dispatchers
scope.launch(Dispatchers.Default) {  // CPU-bound
    heavyComputation()
}

scope.launch(Dispatchers.IO) {  // IO-bound
    networkCall()
}
```

**Severity:** Warning (configurable)

---

### 5. CancellationExceptionSubclass

**Detects:** Classes that extend `CancellationException`.

```kotlin
// ❌ BAD - Domain error extending CancellationException
class UserNotFoundException : CancellationException("User not found")

// ✅ GOOD - Regular exception for domain errors
class UserNotFoundException : Exception("User not found")
```

**Severity:** Error (configurable)

---

### 6. JobInBuilderContext

**Detects:** `Job()` or `SupervisorJob()` passed directly to coroutine builders.

```kotlin
// ❌ BAD - Job() breaks structured concurrency
scope.launch(Job()) {
    doWork()  // Orphan coroutine
}

// ✅ GOOD - Use supervisorScope
suspend fun process() = supervisorScope {
    launch { task1() }
    launch { task2() }
}
```

**Severity:** Error (configurable)

---

### 7. SuspendInFinally

**Detects:** Suspend calls in `finally` blocks without `withContext(NonCancellable)`.

```kotlin
// ⚠️ WARNING - Suspend call without NonCancellable
try {
    doWork()
} finally {
    saveToDb()  // May not execute if cancelled
}

// ✅ GOOD - Wrapped in NonCancellable
try {
    doWork()
} finally {
    withContext(NonCancellable) {
        saveToDb()  // Will execute even if cancelled
    }
}
```

**Severity:** Warning (configurable)

---

### 8. CancellationExceptionSwallowed

**Detects:** `catch(Exception)` that may swallow `CancellationException`.

```kotlin
// ⚠️ WARNING - May swallow cancellation
suspend fun bad() {
    try { work() }
    catch (e: Exception) { log(e) }
}

// ✅ GOOD - Explicit CancellationException handling
suspend fun good() {
    try { work() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { log(e) }
}
```

**Severity:** Warning (configurable)

---

### 9. AsyncWithoutAwait

**Detects:** `async` without `await()`.

```kotlin
// ❌ BAD - async without await
val deferred = scope.async { computeValue() }
// deferred never used

// ✅ GOOD - async with await
val deferred = scope.async { computeValue() }
val result = deferred.await()
```

**Severity:** Error (configurable)

---

## Android-Specific Rules

These rules leverage Android context and are not available in Detekt/Compiler Plugin.

### 10. MainDispatcherMisuse ⭐ High Priority

**Detects:** Blocking code on `Dispatchers.Main`.

```kotlin
// ❌ BAD - Blocking call on Main dispatcher
viewModelScope.launch(Dispatchers.Main) {
    Thread.sleep(1000)  // Freezes UI!
    inputStream.read()  // Blocks Main thread
}

// ✅ GOOD - Use Dispatchers.IO for blocking operations
viewModelScope.launch(Dispatchers.Main) {
    updateUI()  // Quick UI update
    withContext(Dispatchers.IO) {
        inputStream.read()  // Blocking I/O on IO dispatcher
    }
}
```

**Detected Methods:**

| Category | Methods |
|----------|---------|
| Thread | `Thread.sleep()` |
| I/O Streams | `InputStream.read()`, `OutputStream.write()`, `BufferedReader.readLine()` |
| JDBC | `Statement.execute*()`, `Connection.prepareStatement()`, `ResultSet.next()` |
| HTTP | `okhttp3.Call.execute()`, `retrofit2.Call.execute()` |
| Concurrency | `Future.get()`, `BlockingQueue.take()`, `CountDownLatch.await()`, `Semaphore.acquire()` |

**Severity:** Error (configurable)

---

### 11. ViewModelScopeLeak

**Detects:** Custom scopes in ViewModels or `viewModelScope` usage outside ViewModels.

```kotlin
// ❌ BAD - Custom scope in ViewModel
class MyViewModel : ViewModel() {
    private val customScope = CoroutineScope(Dispatchers.Main)

    fun load() {
        customScope.launch { }  // Not lifecycle-aware
    }
}

// ✅ GOOD - Use official viewModelScope
class MyViewModel : ViewModel() {
    fun load() {
        viewModelScope.launch { fetchData() }
    }
}
```

**Severity:** Error (configurable)

---

### 12. LifecycleAwareScope

**Validates:** Correct usage of `lifecycleScope` in Activities/Fragments.

```kotlin
// ✅ GOOD - lifecycleScope in Activity
class MainActivity : AppCompatActivity() {
    fun load() {
        lifecycleScope.launch { fetchData() }
    }
}

// ❌ BAD - lifecycleScope used outside LifecycleOwner
class MyRepository {
    fun load(activity: Activity) {
        activity.lifecycleScope.launch { }  // Wrong context
    }
}
```

**Severity:** Error (configurable)

---

## Additional Rules

### 13. UnstructuredLaunch

**Detects:** `launch/async` without structured scope (heuristic).

```kotlin
// ❌ BAD - Scope not annotated with @StructuredScope
fun process(scope: CoroutineScope) {
    scope.launch { doWork() }
}

// ✅ GOOD - Using @StructuredScope annotation
fun process(@StructuredScope scope: CoroutineScope) {
    scope.launch { doWork() }
}
```

**Severity:** Error (configurable)

---

### 14. RedundantLaunchInCoroutineScope

**Detects:** Redundant `launch` at the end of `coroutineScope`.

```kotlin
// ⚠️ WARNING - Redundant launch
suspend fun bad() = coroutineScope {
    launch { work() }  // Unnecessary
}

// ✅ GOOD - Direct execution
suspend fun good() = coroutineScope {
    work()  // Direct, without launch
}
```

**Severity:** Warning (configurable)

---

### 15. RunBlockingWithDelayInTest

**Detects:** `runBlocking` + `delay` in test files.

```kotlin
// ❌ BAD - Slow test (waits real time)
@Test
fun `test something`() = runBlocking {
    delay(1000)  // Waits 1 real second
    assertEquals(expected, result)
}

// ✅ GOOD - Fast test (virtual time)
@Test
fun `test something`() = runTest {
    delay(1000)  // Instant - virtual time
    assertEquals(expected, result)
}
```

**Applies to Files:**
- `*Test.kt`
- `*Tests.kt`
- `*Spec.kt`
- Files in `/test/` or `/androidTest/`

**Severity:** Warning (configurable)

---

### 16. LoopWithoutYield ⚠️ Partial

**Detects:** Loops without cooperation points in suspend functions (heuristic).

```kotlin
// ❌ BAD - Cannot be cancelled during loop
suspend fun processItems(items: List<Item>) {
    for (item in items) {
        heavyComputation(item)  // No cooperation
    }
}

// ✅ GOOD - With ensureActive()
suspend fun processItems(items: List<Item>) {
    for (item in items) {
        ensureActive()  // Check cancellation
        heavyComputation(item)
    }
}
```

**Recognized Cooperation Points:**
- `yield()`
- `ensureActive()`
- `delay()`
- `suspendCancellableCoroutine`
- `withTimeout()` / `withTimeoutOrNull()`

**Severity:** Warning (configurable)

**Note:** This is a heuristic rule. May have false positives if the loop contains suspend calls that provide cooperation points.

---

### 17. ScopeReuseAfterCancel ⚠️ Partial

**Detects:** Obvious cases of cancelled scope being reused.

```kotlin
// ❌ BAD - Scope cancelled and then reused
fun process(scope: CoroutineScope) {
    scope.cancel()
    scope.launch { work() }  // Won't work
}

// ✅ GOOD - Use cancelChildren() instead
fun process(scope: CoroutineScope) {
    scope.coroutineContext.job.cancelChildren()
    scope.launch { work() }  // Still works
}
```

**Severity:** Warning (configurable)

**Note:** This is a heuristic rule. Only detects obvious cases in the same function. Complex cases requiring flow analysis are not detected.

---

## Running Lint

```bash
# Full analysis
./gradlew lint

# Specific module analysis
./gradlew :app:lint

# With HTML report (generated at app/build/reports/lint-results.html)
./gradlew lint
# Then open: app/build/reports/lint-results.html
```

---

## CI Integration

### GitHub Actions

```yaml
# .github/workflows/lint.yml
name: Android Lint

on: [push, pull_request]

jobs:
  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Lint
        run: ./gradlew lint
      - name: Upload Report
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: lint-report
          path: app/build/reports/lint-results*.html
```

---

## Suppressing Rules

### Suppress for Specific Code

```kotlin
@SuppressLint("MainDispatcherMisuse")
suspend fun legitimateBlockingCall() {
    // Documented special case
    viewModelScope.launch(Dispatchers.Main) {
        Thread.sleep(100)  // Documented exception
    }
}
```

### Suppress at File Level

```kotlin
@file:SuppressLint("LoopWithoutYield")
package com.example.intensive
```

### Suppress in Configuration

```xml
<lint>
    <issue id="GlobalScopeUsage" severity="ignore">
        <ignore path="**/legacy/**" />
    </issue>
</lint>
```

---

## Comparison with Other Tools

| Aspect | Compiler Plugin | Detekt | Android Lint |
|--------|-----------------|--------|--------------|
| Platform | Multiplatform | Multiplatform | Android only |
| IDE Integration | Basic | Basic | Native (Android Studio) |
| Quick Fixes | No | No | Yes (coming soon) |
| XML Analysis | No | No | Yes |
| Android Context | No | No | Yes (ViewModel, Lifecycle) |
| Blocks Compilation | Yes (errors) | No | Configurable |
| CI/CD | `./gradlew build` | `./gradlew detekt` | `./gradlew lint` |
| Rules Count | 11 | 9 | 17 |

### Advantages of Android Lint

| Advantage | Description |
|-----------|-------------|
| Native IDE Integration | Real-time detection in Android Studio |
| Quick Fixes | Automatic correction suggestions (coming soon) |
| XML Resource Analysis | Can detect problems in layouts |
| Manifest Access | Android component context awareness |
| Android-Specific Detection | Main dispatcher, ViewModel lifecycle, etc. |
| Non-Blocking | Warnings/Errors are configurable |
| CI/CD Integration | `./gradlew lint` in pipelines |

---

## Usage Strategy

### Option 1: Android Lint Only (Flexible)

- Warnings don't block compilation
- Native Android Studio integration
- Quick fixes for gradual migration
- Ideal for existing Android projects

### Option 2: Compiler Plugin + Lint (Maximum Safety)

- Compiler Plugin: Critical errors block compilation
- Lint: Additional warnings + Android-specific detection

### Option 3: Detekt + Lint (Complete Static Analysis)

- Detekt: Multiplatform analysis
- Lint: Android-specific analysis + quick fixes

### Option 4: Compiler Plugin Only (Strict)

- Errors block compilation
- Maximum precision
- No Android-specific detection

---

## Limitations

| Limitation | Description |
|------------|-------------|
| Android Only | Does not work on pure multiplatform projects (KMP common) |
| Lower Precision | Less precise than compiler plugin on some complex rules |
| Heuristics | Uses heuristics for annotations and framework scopes |
| False Positives | Possible in partial rules (LoopWithoutYield, ScopeReuseAfterCancel) |

---

## Roadmap

| Status | Feature |
|--------|---------|
| 🔲 | Quick fixes for all rules |
| 🔲 | Improved heuristics to reduce false positives |
| 🔲 | Advanced flow analysis support |
| 🔲 | Android Studio inspections integration |

---

## License

```
Copyright 2026 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```
