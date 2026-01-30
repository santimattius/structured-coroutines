# Detekt Rules for Structured Coroutines

Custom Detekt rules for enforcing structured concurrency best practices in Kotlin Coroutines.

## Table of Contents

- [Installation](#installation)
- [Configuration](#configuration)
- [Rules Overview](#rules-overview)
- [Compiler Plugin Rules](#compiler-plugin-rules)
- [Detekt-Only Rules](#detekt-only-rules)
- [Running Detekt](#running-detekt)
- [Kotlin Multiplatform Configuration](#kotlin-multiplatform-configuration)
- [CI Integration](#ci-integration)
- [Suppressing Rules](#suppressing-rules)

---

## Installation

### 1. Add Detekt to Your Project

```kotlin
// build.gradle.kts
plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.7"
}
```

### 2. Add Custom Rules Dependency

```kotlin
dependencies {
    detektPlugins("io.github.santimattius:structured-coroutines-detekt-rules:0.1.0")
}
```

### 3. Local Development Setup

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenLocal()  // For local version
        gradlePluginPortal()
        mavenCentral()
    }
}
```

---

## Configuration

Create or update your `detekt.yml` configuration file:

```yaml
structured-coroutines:

  # ============================================
  # Compiler Plugin Rules
  # ============================================

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

  # ============================================
  # Detekt-Only Rules (Static Analysis)
  # ============================================

  BlockingCallInCoroutine:
    active: true
    excludes: ['commonMain', 'iosMain', 'jsMain']

  RunBlockingWithDelayInTest:
    active: true

  ExternalScopeLaunch:
    active: true

  LoopWithoutYield:
    active: true
```

---

## Rules Overview

### Summary Table

| Rule | Category | Severity | Description |
|------|----------|----------|-------------|
| `GlobalScopeUsage` | Compiler Plugin | Error | Detects `GlobalScope.launch/async` |
| `InlineCoroutineScope` | Compiler Plugin | Error | Detects `CoroutineScope(...).launch/async` |
| `RunBlockingInSuspend` | Compiler Plugin | Warning | Detects `runBlocking` in suspend functions |
| `DispatchersUnconfined` | Compiler Plugin | Warning | Detects `Dispatchers.Unconfined` usage |
| `CancellationExceptionSubclass` | Compiler Plugin | Error | Detects classes extending `CancellationException` |
| `BlockingCallInCoroutine` | Detekt-Only | Warning | Detects blocking calls inside coroutines |
| `RunBlockingWithDelayInTest` | Detekt-Only | Warning | Detects `runBlocking` + `delay` in tests |
| `ExternalScopeLaunch` | Detekt-Only | Warning | Detects launch on external scopes from suspend functions |
| `LoopWithoutYield` | Detekt-Only | Warning | Detects loops without cooperation points |

### Best Practices Reference

| Rule | Best Practice |
|------|---------------|
| `GlobalScopeUsage` | 1.1 - Using GlobalScope in Production Code |
| `InlineCoroutineScope` | 1.3 - Breaking Structured Concurrency |
| `RunBlockingInSuspend` | 2.2 - Using runBlocking Inside Suspend Functions |
| `DispatchersUnconfined` | 3.2 - Abusing Dispatchers.Unconfined |
| `CancellationExceptionSubclass` | 5.2 - Extending CancellationException for Domain Errors |
| `BlockingCallInCoroutine` | 3.1 - Mixing Blocking Code with Wrong Dispatchers |
| `RunBlockingWithDelayInTest` | 6.1 - Slow Tests with Real Delays |
| `ExternalScopeLaunch` | 1.3 - Breaking Structured Concurrency |
| `LoopWithoutYield` | 4.1 - Ignoring Cancellation in Intensive Loops |

---

## Compiler Plugin Rules

These rules replicate the Compiler Plugin functionality as Detekt rules, allowing you to use only Detekt without the compiler plugin.

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

// ✅ GOOD - Annotated with @StructuredScope
fun process(@StructuredScope scope: CoroutineScope) {
    scope.launch { fetchData() }
}

// ✅ GOOD - Structured builders
suspend fun process() = coroutineScope {
    launch { fetchData() }
}
```

**Severity:** Error or Warning (configurable)

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

// ✅ GOOD - Annotated with @StructuredScope
class Repository(@StructuredScope private val scope: CoroutineScope) {
    fun fetch() {
        scope.launch { fetchData() }
    }
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

**Severity:** Error or Warning (configurable)

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

@Test
fun testSomething() = runBlocking {
    val result = fetchData()
    assertEquals(expected, result)
}
```

**Severity:** Warning

---

### 4. DispatchersUnconfined

**Detects:** Usage of `Dispatchers.Unconfined` in coroutine builders.

```kotlin
// ⚠️ WARNING - Dispatchers.Unconfined
scope.launch(Dispatchers.Unconfined) {
    doWork()  // Unpredictable thread
}

withContext(Dispatchers.Unconfined) {
    processData()  // Can execute on any thread
}

// ✅ GOOD - Appropriate dispatchers
scope.launch(Dispatchers.Default) {  // CPU-bound
    heavyComputation()
}

scope.launch(Dispatchers.IO) {  // IO-bound
    networkCall()
    fileOperation()
}

scope.launch(Dispatchers.Main) {  // UI updates
    updateUI()
}
```

**Severity:** Warning

**Note:** `Dispatchers.Unconfined` may be acceptable in very specific cases (testing, temporary legacy code).

---

### 5. CancellationExceptionSubclass

**Detects:** Classes that extend `CancellationException`.

```kotlin
// ❌ BAD - Domain error extending CancellationException
class UserNotFoundException : CancellationException("User not found")

suspend fun fetchUser(id: String) {
    if (user == null) {
        throw UserNotFoundException()  // Treated as cancellation!
    }
}

// ✅ GOOD - Regular exception for domain errors
class UserNotFoundException : Exception("User not found")

suspend fun fetchUser(id: String) {
    if (user == null) {
        throw UserNotFoundException()  // Proper exception handling
    }
}

// ✅ GOOD - Handle cancellation separately
suspend fun process() {
    try {
        fetchUser(id)
    } catch (e: CancellationException) {
        throw e  // Re-throw cancellation
    } catch (e: UserNotFoundException) {
        handleError(e)  // Handle domain error
    }
}
```

**Severity:** Error or Warning (configurable)

---

## Detekt-Only Rules

These rules are only available as Detekt Rules because they require static analysis that is not possible at compile time.

### 6. BlockingCallInCoroutine

**Detects:** Blocking calls inside coroutines.

```kotlin
// ❌ BAD
scope.launch {
    Thread.sleep(1000)           // Blocks the thread
    inputStream.read()           // Blocking I/O
    jdbcStatement.executeQuery() // Blocking JDBC
    okHttpCall.execute()         // Synchronous HTTP
}

// ✅ GOOD
scope.launch {
    delay(1000)                  // Non-blocking
    withContext(Dispatchers.IO) {
        inputStream.read()       // Wrapped properly
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
| Concurrency | `Future.get()`, `BlockingQueue.take()`, `BlockingQueue.put()`, `CountDownLatch.await()`, `Semaphore.acquire()` |

**Platforms:** JVM only

---

### 7. RunBlockingWithDelayInTest

**Detects:** `runBlocking` with `delay()` in test files.

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

---

### 8. ExternalScopeLaunch

**Detects:** Launch on external scope from suspend functions.

```kotlin
// ❌ BAD - Breaks structured concurrency
class MyService(private val scope: CoroutineScope) {
    suspend fun process() {
        scope.launch { work() }  // Not tied to process() lifecycle
    }
}

// ✅ GOOD - Structured concurrency
class MyService {
    suspend fun process() = coroutineScope {
        launch { work() }  // Tied to process() lifecycle
    }
}

// ✅ GOOD - Explicit fire-and-forget (non-suspend)
class MyService(private val scope: CoroutineScope) {
    fun fireAndForget() {
        scope.launch { work() }
    }
}
```

**Excluded Scopes (Framework):**
- `viewModelScope`
- `lifecycleScope`
- `rememberCoroutineScope`

---

### 9. LoopWithoutYield

**Detects:** Loops without cooperation points in suspend functions.

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
        ensureActive()          // Check cancellation
        heavyComputation(item)
    }
}

// ✅ GOOD - With yield()
suspend fun processItems(items: List<Item>) {
    for (item in items) {
        yield()                 // Cooperation point
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

---

## Running Detekt

```bash
# Full analysis
./gradlew detekt

# Specific module analysis (KMP)
./gradlew :composeApp:detekt

# With HTML report (generated in build/reports/detekt/)
./gradlew detekt
# Then open: build/reports/detekt/detekt.html
```

---

## Kotlin Multiplatform Configuration

For KMP projects, configure source sets:

```kotlin
// build.gradle.kts
detekt {
    config.setFrom("${project.rootDir}/config/detekt/detekt.yml")
    baseline = file("$rootDir/detekt-baseline.xml")
    autoCorrect = false

    // Configure source sets for KMP
    source = files(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
        "src/iosMain/kotlin",
        "src/jvmMain/kotlin",
        "src/jsMain/kotlin"
    )
}
```

### Excluding Specific Source Sets

For KMP projects, you can exclude specific source sets from certain rules:

```yaml
structured-coroutines:
  BlockingCallInCoroutine:
    active: true
    # Only analyze JVM code (Thread.sleep, JDBC don't exist on other platforms)
    excludes: ['commonMain', 'iosMain', 'jsMain', 'wasmMain']
```

---

## CI Integration

### GitHub Actions

```yaml
# .github/workflows/detekt.yml
name: Detekt

on: [push, pull_request]

jobs:
  detekt:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Run Detekt
        run: ./gradlew detekt
      - name: Upload Report
        uses: actions/upload-artifact@v4
        if: failure()
        with:
          name: detekt-report
          path: build/reports/detekt/
```

---

## Suppressing Rules

### Suppress for Specific Code

```kotlin
@Suppress("BlockingCallInCoroutine")
suspend fun legitimateBlockingCall() {
    // Documented special case
    Thread.sleep(100)
}
```

### Suppress at File Level

```kotlin
@file:Suppress("LoopWithoutYield")
package com.example.intensive
```

### Suppress in Configuration

```yaml
structured-coroutines:
  GlobalScopeUsage:
    active: true
    excludes:
      - '**/legacy/**'
      - '**/test/**'
```

---

## License

```
Copyright 2026 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```
