# IntelliJ/Android Studio Plugin Documentation

This document provides detailed information about the Structured Coroutines IntelliJ/Android Studio plugin.

## Table of Contents

- [Installation](#installation)
- [Features](#features)
- [Inspections](#inspections)
- [Quick Fixes](#quick-fixes)
- [Intentions](#intentions)
- [Gutter Icons](#gutter-icons)
- [Configuration](#configuration)
- [Building from Source](#building-from-source)
- [K2 Mode Compatibility](#k2-mode-compatibility)
- [Troubleshooting](#troubleshooting)

---

## Installation

### From JetBrains Marketplace (Recommended)

1. Open IntelliJ IDEA or Android Studio
2. Go to **Settings/Preferences** > **Plugins**
3. Search for "Structured Coroutines"
4. Click **Install**
5. Restart the IDE

### From Disk

1. Download the plugin ZIP file from [Releases](https://github.com/santimattius/structured-coroutines/releases)
2. Go to **Settings/Preferences** > **Plugins**
3. Click the gear icon and select **Install Plugin from Disk...**
4. Select the downloaded ZIP file
5. Restart the IDE

### Building and Installing Locally

```bash
# Clone the repository
git clone https://github.com/santimattius/structured-coroutines.git
cd structured-coroutines

# Build the plugin ZIP (IntelliJ Platform Gradle Plugin 2.x)
./gradlew :intellij-plugin:buildPlugin

# The plugin ZIP will be at:
# intellij-plugin/build/distributions/intellij-plugin-<version>.zip
```

Then install from disk: **Settings/Preferences** → **Plugins** → gear icon → **Install Plugin from Disk...** → select the ZIP from `intellij-plugin/build/distributions/`.

---

## Features

The plugin provides four main feature categories:

1. **Real-time Inspections** - Detect coroutine anti-patterns as you type
2. **Quick Fixes** - One-click corrections for detected issues
3. **Intentions** - Refactoring suggestions available via Alt+Enter
4. **Gutter Icons** - Visual indicators for scope type and dispatcher context

---

## Inspections

### Overview

| Inspection | Severity | Description |
|------------|----------|-------------|
| GlobalScopeUsage | ERROR | Detects `GlobalScope.launch/async` |
| MainDispatcherMisuse | WARNING | Detects blocking code on `Dispatchers.Main` |
| ScopeReuseAfterCancel | WARNING | Detects scope cancelled then reused |
| RunBlockingInSuspend | ERROR | Detects `runBlocking` in suspend functions |
| UnstructuredLaunch | WARNING | Detects launch without structured scope |
| AsyncWithoutAwait | WARNING | Detects `async` without `await()` |
| InlineCoroutineScope | ERROR | Detects `CoroutineScope(...).launch` |
| JobInBuilderContext | ERROR | Detects `Job()`/`SupervisorJob()` in builders |
| SuspendInFinally | WARNING | Detects suspend calls in finally without NonCancellable |
| CancellationExceptionSwallowed | WARNING | Detects `catch(Exception)` swallowing cancellation |
| DispatchersUnconfined | WARNING | Detects `Dispatchers.Unconfined` usage |

### Detailed Inspection Descriptions

#### GlobalScopeUsage (ERROR)

**Problem:** `GlobalScope` bypasses structured concurrency, leading to resource leaks.

```kotlin
// ❌ BAD
GlobalScope.launch {
    fetchData()  // Runs until completion regardless of lifecycle
}

// ✅ GOOD
viewModelScope.launch {
    fetchData()  // Cancelled when ViewModel is cleared
}
```

#### MainDispatcherMisuse (WARNING)

**Problem:** Blocking calls on `Dispatchers.Main` can cause ANRs (Android) or UI freezes.

```kotlin
// ❌ BAD
withContext(Dispatchers.Main) {
    Thread.sleep(1000)  // Blocks UI thread!
}

// ✅ GOOD
withContext(Dispatchers.IO) {
    Thread.sleep(1000)  // Safe - IO thread pool
}
```

#### ScopeReuseAfterCancel (WARNING)

**Problem:** A cancelled scope cannot launch new coroutines.

```kotlin
// ❌ BAD
fun process(scope: CoroutineScope) {
    scope.cancel()
    scope.launch { work() }  // Silently fails!
}

// ✅ GOOD
fun process(scope: CoroutineScope) {
    scope.coroutineContext.job.cancelChildren()
    scope.launch { work() }  // Works - scope is still active
}
```

#### RunBlockingInSuspend (ERROR)

**Problem:** `runBlocking` in suspend functions blocks the thread, defeating coroutines.

```kotlin
// ❌ BAD
suspend fun fetchData() {
    runBlocking {  // Blocks the thread!
        delay(1000)
    }
}

// ✅ GOOD
suspend fun fetchData() {
    delay(1000)  // Suspends without blocking
}
```

#### AsyncWithoutAwait (WARNING)

**Problem:** `async` creates a `Deferred` that should be awaited.

```kotlin
// ❌ BAD - Result never used
scope.async {
    computeValue()
}

// ✅ GOOD - Use the result
val result = scope.async { computeValue() }.await()

// ✅ GOOD - Use launch if result not needed
scope.launch {
    computeValue()
}
```

#### JobInBuilderContext (ERROR)

**Problem:** `Job()`/`SupervisorJob()` in builders breaks cancellation hierarchy.

```kotlin
// ❌ BAD
scope.launch(Job()) {
    work()  // Won't be cancelled with parent!
}

// ✅ GOOD
supervisorScope {
    launch { work() }  // Proper structured concurrency
}
```

#### SuspendInFinally (WARNING)

**Problem:** Suspend calls in finally may not complete if coroutine is cancelled.

```kotlin
// ❌ BAD
try { work() } finally {
    saveToDb()  // May not complete!
}

// ✅ GOOD
try { work() } finally {
    withContext(NonCancellable) {
        saveToDb()  // Guaranteed to complete
    }
}
```

#### CancellationExceptionSwallowed (WARNING)

**Problem:** Catching generic `Exception` swallows `CancellationException`.

```kotlin
// ❌ BAD
suspend fun work() {
    try { fetchData() }
    catch (e: Exception) { log(e) }  // Breaks cancellation!
}

// ✅ GOOD
suspend fun work() {
    try { fetchData() }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { log(e) }
}
```

---

## Quick Fixes

Each inspection provides one or more quick fixes accessible via Alt+Enter (or the lightbulb icon):

| Quick Fix | Applies To |
|-----------|------------|
| Replace GlobalScope with viewModelScope | GlobalScopeUsage |
| Replace GlobalScope with lifecycleScope | GlobalScopeUsage |
| Replace GlobalScope with coroutineScope { } | GlobalScopeUsage |
| Wrap with withContext(Dispatchers.IO) | MainDispatcherMisuse |
| Replace cancel() with cancelChildren() | ScopeReuseAfterCancel |
| Remove runBlocking | RunBlockingInSuspend |
| Add .await() | AsyncWithoutAwait |
| Convert async to launch | AsyncWithoutAwait |
| Wrap with withContext(NonCancellable) | SuspendInFinally |
| Add CancellationException catch clause | CancellationExceptionSwallowed |
| Replace with supervisorScope { } | JobInBuilderContext |

---

## Intentions

Intentions are available via Alt+Enter when the cursor is on relevant code:

### Migrate to viewModelScope

**Availability:** Inside a ViewModel class, on a launch/async call

Converts any coroutine launch to use `viewModelScope`:

```kotlin
// Before
someScope.launch { work() }

// After
viewModelScope.launch { work() }
```

### Migrate to lifecycleScope

**Availability:** Inside Activity/Fragment, on a launch/async call

Converts any coroutine launch to use `lifecycleScope`:

```kotlin
// Before
someScope.launch { work() }

// After
lifecycleScope.launch { work() }
```

### Wrap with coroutineScope { }

**Availability:** Inside a suspend function

Wraps the function body with `coroutineScope` builder:

```kotlin
// Before
suspend fun process() {
    launch { task1() }
    launch { task2() }
}

// After
suspend fun process() = coroutineScope {
    launch { task1() }
    launch { task2() }
}
```

### Convert launch to async

**Availability:** On any launch call

Converts `launch` to `async` for returning a `Deferred`:

```kotlin
// Before
scope.launch { work() }

// After
scope.async { work() }
```

### Extract suspend function

**Availability:** Inside a coroutine builder lambda

Extracts the lambda body into a separate suspend function:

```kotlin
// Before
scope.launch {
    val data = fetchData()
    processData(data)
    saveResult()
}

// After
scope.launch { performWork() }

private suspend fun performWork() {
    val data = fetchData()
    processData(data)
    saveResult()
}
```

---

## Gutter Icons

### Scope Type Icons

The plugin shows colored dots in the gutter to indicate scope types:

| Color | Scope Type | Safety |
|-------|------------|--------|
| 🟢 Green | viewModelScope | Safe - tied to ViewModel lifecycle |
| 🔵 Blue | lifecycleScope | Safe - tied to lifecycle |
| 🟣 Purple | coroutineScope/supervisorScope | Safe - structured builder |
| ⚪ Gray | Custom scope | Depends on implementation |
| 🔴 Red | GlobalScope | Unsafe - no lifecycle |

### Dispatcher Context Icons

Letter badges indicate the dispatcher:

| Icon | Dispatcher | Thread |
|------|------------|--------|
| **M** (Orange) | Dispatchers.Main | UI thread |
| **I** (Blue) | Dispatchers.IO | I/O thread pool |
| **D** (Green) | Dispatchers.Default | CPU thread pool |
| **U** (Red) | Dispatchers.Unconfined | Unpredictable |

---

## Configuration

### Enabling/Disabling Inspections

1. Go to **Settings/Preferences** > **Editor** > **Inspections**
2. Navigate to **Kotlin** > **Coroutines**
3. Check/uncheck individual inspections
4. Adjust severity levels as needed (Error, Warning, Weak Warning)

### Inspection Profiles

You can create custom inspection profiles:

1. Go to **Settings/Preferences** > **Editor** > **Inspections**
2. Click the gear icon next to the profile dropdown
3. Select **Duplicate** to create a copy
4. Customize the new profile

### Suppressing Inspections

To suppress an inspection for specific code:

```kotlin
// Suppress for a statement
@Suppress("GlobalScopeUsage")
GlobalScope.launch { work() }

// Suppress for a function
@Suppress("RunBlockingInSuspend")
suspend fun legacy() {
    runBlocking { /* ... */ }
}

// Suppress for a file
@file:Suppress("GlobalScopeUsage")
```

---

## Building from Source

### Prerequisites

- JDK 21+ (required for IntelliJ Platform 2024.3+)
- Gradle 8.0+

### Commands

```bash
# Build the plugin ZIP for local install or distribution
./gradlew :intellij-plugin:buildPlugin

# Build and run tests
./gradlew :intellij-plugin:build

# Run tests
./gradlew :intellij-plugin:test

# Verify plugin compatibility
./gradlew :intellij-plugin:verifyPlugin

# Run IDE sandbox for testing
./gradlew :intellij-plugin:runIde
```

### Project Structure

```
intellij-plugin/
├── build.gradle.kts
├── src/main/kotlin/
│   └── io/github/santimattius/structured/intellij/
│       ├── StructuredCoroutinesBundle.kt
│       ├── inspections/
│       ├── quickfixes/
│       ├── intentions/
│       ├── guttericons/
│       └── utils/
├── src/main/resources/
│   ├── META-INF/plugin.xml
│   └── messages/StructuredCoroutinesBundle.properties
└── src/test/kotlin/
```

---

## K2 Mode Compatibility

### Overview

The plugin fully supports the Kotlin K2 compiler mode, which is the new Kotlin compiler frontend used in recent versions of IntelliJ IDEA and Android Studio.

### What is K2 Mode?

K2 is Kotlin's new compiler frontend that provides:
- Faster compilation times
- Better IDE performance
- Improved type inference
- More accurate code analysis

Android Studio (starting with Ladybug) and IntelliJ IDEA (2024.2+) use K2 mode by default for Kotlin code analysis.

### Compatibility Declaration

The plugin declares K2 support via the `supportsKotlinPluginMode` extension in `plugin.xml`:

```xml
<extensions defaultExtensionNs="org.jetbrains.kotlin">
    <supportsKotlinPluginMode supportsK2="true" />
</extensions>
```

### Technical Implementation Details

For K2 compatibility, the plugin implements specific patterns:

#### Line Marker Providers

Line markers (gutter icons) must work with **leaf PSI elements** in K2 mode. The implementation uses:

```kotlin
override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
    // Work with leaf elements only for K2 compatibility
    if (element !is LeafPsiElement) return null
    if (element.elementType != KtTokens.IDENTIFIER) return null

    // Get parent KtNameReferenceExpression from the leaf
    val nameRef = element.parent as? KtNameReferenceExpression ?: return null

    // Continue with analysis...
}
```

#### PSI-Based Analysis

The plugin uses PSI-based analysis (not descriptor-based), which works identically in both K1 and K2 modes:

- `KtCallExpression` for call detection
- `KtNameReferenceExpression` for identifier analysis
- `KtVisitorVoid` for tree traversal
- Standard PSI utilities (`PsiTreeUtil`, `getParentOfType`, etc.)

### Verifying K2 Compatibility

To verify the plugin works in K2 mode:

1. **Check K2 Mode is Enabled:**
   - IntelliJ: **Settings** > **Languages & Frameworks** > **Kotlin** > **Enable K2 Kotlin Mode**
   - Android Studio: K2 is enabled by default in Ladybug+

2. **Test Plugin Features:**
   - Open a Kotlin file with coroutine code
   - Verify gutter icons appear for `launch`/`async` calls
   - Verify inspections highlight problematic patterns
   - Verify quick fixes work correctly

3. **Check Logs for Errors:**
   - **Help** > **Show Log in Explorer/Finder**
   - Search for plugin-related errors

### K2 Implementation Notes

The plugin implements K2 compatibility through:

1. Proper `supportsKotlinPluginMode` declaration in `plugin.xml`
2. Leaf element handling in line marker providers
3. PSI-only analysis (no K1-specific descriptor APIs)

---

## Troubleshooting

### Plugin Not Loading

1. Check IDE compatibility (requires IntelliJ 2024.3+)
2. Verify Kotlin plugin is installed and enabled
3. Check **Help** > **Show Log** for errors

### Inspections Not Working

1. Ensure inspections are enabled in Settings
2. Check if the file is in a source root
3. Verify Kotlin plugin is working (try a basic Kotlin inspection)
4. If using K2 mode, ensure you have the latest plugin version with K2 support

### Gutter Icons Not Appearing (K2 Mode)

If gutter icons don't appear in K2 mode:

1. **Verify K2 support:** Ensure the latest plugin version is installed
2. **Restart IDE:** After enabling/disabling K2 mode, restart the IDE
3. **Invalidate caches:** **File** > **Invalidate Caches** > **Invalidate and Restart**
4. **Check file type:** Gutter icons only appear in `.kt` files with coroutine code
5. **Verify code patterns:** Icons appear on `launch`, `async`, `withContext` calls

### Performance Issues

If the plugin causes slowdowns:

1. Disable unused inspections
2. Exclude large directories from inspection
3. Report issues with profiler data

### Reporting Issues

Please report issues at:
https://github.com/santimattius/structured-coroutines/issues

Include:
- IDE version
- Plugin version
- Kotlin version
- Minimal code sample reproducing the issue
- Stack trace (if applicable)

---

## Compatibility

### IDE Versions

| IDE | Minimum Version | Status |
|-----|-----------------|--------|
| IntelliJ IDEA Community | 2024.3 | ✅ Supported |
| IntelliJ IDEA Ultimate | 2024.3 | ✅ Supported |
| Android Studio | Ladybug (2024.2) | ✅ Supported |

### Kotlin Plugin Modes

| Mode | Status | Notes |
|------|--------|-------|
| K1 (Classic) | ✅ Supported | Full functionality |
| K2 (New) | ✅ Supported | Full functionality |

### Feature Support Matrix

| Feature | K1 Mode | K2 Mode |
|---------|---------|---------|
| Inspections | ✅ | ✅ |
| Quick Fixes | ✅ | ✅ |
| Intentions | ✅ | ✅ |
| Gutter Icons (Scope) | ✅ | ✅ |
| Gutter Icons (Dispatcher) | ✅ | ✅ |

---

## Version History

### v0.1.0

**Initial Release**

- 11 inspections for coroutine best practices
- 9 quick fixes for automatic corrections
- 5 intentions for refactoring
- 2 gutter icon providers (scope type and dispatcher context)
- Full K2 compiler mode support

---

## License

```
Copyright 2026 Santiago Mattiauda

Licensed under the Apache License, Version 2.0
```
