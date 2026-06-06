<p align="center">
  <img  src="https://github.com/santimattius/structured-coroutines/blob/main/docs/structured-coroutines.png?raw=true"/>
</p>

# Structured Coroutines

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](LICENSE)
[![Multiplatform](https://img.shields.io/badge/Multiplatform-Supported-orange.svg)](https://kotlinlang.org/docs/multiplatform.html)

A comprehensive toolkit for enforcing **structured concurrency** in Kotlin Coroutines, inspired by
Swift Concurrency. It provides multiple layers of protection through compile-time checks and static
analysis.

## Project Status

| Module                        | Status                                                  | Documentation                                                                                               |
|-------------------------------|---------------------------------------------------------|-------------------------------------------------------------------------------------------------------------|
| Compiler Plugin               | ✅ Complete (14 FIR checkers)                            | [gradle-plugin/README.md](gradle-plugin/README.md)                                                          |
| Gradle Plugin                 | ✅ Complete (profiles incl. Spring/Ktor)                 | [gradle-plugin/README.md](gradle-plugin/README.md)                                                          |
| Detekt Rules                  | ✅ Complete (40 rules)                                   | [detekt-rules/README.md](detekt-rules/README.md)                                                            |
| Android Lint                  | ✅ Complete (35 detectors)                               | [lint-rules/README.md](lint-rules/README.md)                                                                |
| IntelliJ Plugin               | ✅ Complete (35 inspections, tool window, Flow Analyzer) | [intellij-plugin/README.md](intellij-plugin/README.md)                                                    |
| Annotations                   | ✅ Complete                                              | [annotations/README.md](annotations/README.md)                                                              |
| Sample                        | ✅ Compilation examples per rule                         | [compilation/README](sample/src/main/kotlin/io/github/santimattius/structured/sample/compilation/README.md) |
| Sample (Detekt)               | ✅ Detekt rule validation (30+ examples)                 | [sample-detekt/README.md](sample-detekt/README.md)                                                          |
| Kotlin Coroutines Agent Skill | ✅ AI/agent guidance                                     | [docs/KOTLIN_COROUTINES_SKILL.md](docs/KOTLIN_COROUTINES_SKILL.md)                                          |

---

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
3. **Android Lint Rules** - Android-specific static analysis with quick fixes

---

## 📦 Toolkit Components

| Module            | Purpose                        | When                   |
|-------------------|--------------------------------|------------------------|
| `compiler`        | K2/FIR Compiler Plugin         | Compile-time errors    |
| `detekt-rules`    | Detekt custom rules            | Static analysis        |
| `lint-rules`      | Android Lint rules             | Android projects       |
| `intellij-plugin` | IntelliJ/Android Studio Plugin | Real-time IDE analysis |
| `annotations`     | `@StructuredScope` annotation  | Runtime/Compile        |
| `gradle-plugin`   | Gradle integration             | Build configuration    |

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
- 📱 **Android Lint integration** - Android-specific rules with quick fixes
- 📋 **Structured Coroutines tool window** (IntelliJ) - view all findings for the current file in one
  place
- 📂 **Compilation samples** - one example per compiler rule in the sample project
- 📂 **Detekt validation samples** - `sample-detekt` module with one example per Detekt rule (
  `./gradlew :sample-detekt:detekt`)
- 🤖 **Kotlin Coroutines Agent Skill** - consistent AI/agent-driven guidance for coroutine code; use it as a **decision resource** to answer "which scope?", "launch vs async?", "withTimeout or withTimeoutOrNull?", "StateFlow or SharedFlow?"

---

## 🚨 Rules Overview

**51 documented patterns** (v1.0.0) — single source of truth: [`docs/rule-codes.yml`](docs/rule-codes.yml).  
Full descriptions, examples, and suppression IDs: [`docs/BEST_PRACTICES_COROUTINES.md`](docs/BEST_PRACTICES_COROUTINES.md), [`docs/SUPPRESSING_RULES.md`](docs/SUPPRESSING_RULES.md).

| Layer | Count | Coverage |
|-------|-------|----------|
| **Compiler (FIR)** | 14 checkers | 7 errors + 7 warnings; INTEROP_001/002 in K2 |
| **Detekt** | 40 rules | KMP source-set aware; backend + Compose profiles |
| **Android Lint** | 35 detectors | Android/Compose + shared compiler rules |
| **IntelliJ** | 35 inspections | Real-time analysis, quick fixes, Flow Chain Analyzer |

**v1.0 highlights:** COMPOSE_002/003, TEST_005/006, FLOW_009/011, INTEROP_003/004, DEBUG_001 (opt-in).  
Platform guides: [Compose](docs/guides/compose.md) · [KMP](docs/guides/kmp.md) · [Ktor](docs/guides/ktor.md) · [Spring](docs/guides/spring.md).

Per-module rule lists and configuration examples: [detekt-rules](detekt-rules/README.md) · [lint-rules](lint-rules/README.md) · [intellij-plugin](intellij-plugin/README.md).

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
    implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
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

### Android Lint Rules

```kotlin
// build.gradle.kts (Android project)
dependencies {
    lintChecks("io.github.santimattius:structured-coroutines-lint-rules:0.1.0")
}
```

**Note:** Android Lint Rules are only available for Android projects. For multiplatform projects,
use the Compiler Plugin or Detekt Rules.

### IntelliJ/Android Studio Plugin

Install from JetBrains Marketplace or build from source:

```bash
# Build the plugin
./gradlew :intellij-plugin:build

# Run IDE sandbox for testing
./gradlew :intellij-plugin:runIde
```

Or install manually:

1. Go to **Settings** > **Plugins** > **Install Plugin from Disk**
2. Select `intellij-plugin/build/distributions/intellij-plugin-*.zip`

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
                implementation("io.github.santimattius:structured-coroutines-annotations:0.1.0")
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

| Scope                      | Framework         | Package                    |
|----------------------------|-------------------|----------------------------|
| `viewModelScope`           | Android ViewModel | `androidx.lifecycle`       |
| `lifecycleScope`           | Android Lifecycle | `androidx.lifecycle`       |
| `rememberCoroutineScope()` | Jetpack Compose   | `androidx.compose.runtime` |

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
    try {
        work()
    } catch (e: Exception) {
        log(e)
    }
}

// ✅ Handle separately
suspend fun good() {
    try {
        work()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log(e)
    }
}
```

### 7. NonCancellable in Finally

```kotlin
// ⚠️ WARNING - May not execute
try {
    work()
} finally {
    saveToDb()  // Suspend call
}

// ✅ Protected
try {
    work()
} finally {
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
    excludes: [ 'commonMain', 'iosMain' ]  # JVM-only
  RunBlockingWithDelayInTest:
    active: true
  ExternalScopeLaunch:
    active: true
  LoopWithoutYield:
    active: true
```

**📖 Full Documentation:** [Detekt Rules Documentation](detekt-rules/README.md)

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
│   ├── CancellationExceptionSwallowedChecker
│   ├── UnusedDeferredChecker
│   ├── RedundantLaunchInCoroutineScopeChecker
│   └── LoopWithoutYieldChecker
├── detekt-rules/         # Detekt Custom Rules
│   ├── GlobalScopeUsageRule
│   ├── InlineCoroutineScopeRule
│   ├── RunBlockingInSuspendRule
│   ├── DispatchersUnconfinedRule
│   ├── CancellationExceptionSubclassRule
│   ├── BlockingCallInCoroutineRule
│   ├── RunBlockingWithDelayInTestRule
│   ├── ExternalScopeLaunchRule
│   ├── LoopWithoutYieldRule
│   ├── ScopeReuseAfterCancelRule
│   ├── ChannelNotClosedRule
│   ├── ConsumeEachMultipleConsumersRule
│   └── FlowBlockingCallRule
├── lint-rules/           # Android Lint Rules
│   ├── GlobalScopeUsageDetector
│   ├── MainDispatcherMisuseDetector
│   ├── ViewModelScopeLeakDetector
│   └── ... (21 rules total)
├── intellij-plugin/      # IntelliJ/Android Studio Plugin
│   ├── inspections/      # 13 real-time inspections (incl. LoopWithoutYield, LifecycleAwareFlowCollection)
│   ├── quickfixes/       # 12 automatic quick fixes
│   ├── intentions/       # 6 refactoring intentions (incl. Convert to runTest)
│   ├── guttericons/      # Scope & dispatcher visualization
│   └── view/             # Tool window (findings list, runner, tree visitor)
├── gradle-plugin/        # Gradle Integration
├── sample/               # Examples
│   └── compilation/     # One example per compiler rule (errors & warnings)
└── kotlin-coroutines-skill/  # AI/agent skill for coroutine best practices
```

---

## 🌍 Supported Platforms

| Platform | Compiler Plugin | Detekt Rules | Android Lint | IDE Plugin |
|----------|-----------------|--------------|--------------|------------|
| JVM      | ✅               | ✅            | ❌            | ✅          |
| Android  | ✅               | ✅            | ✅            | ✅          |
| iOS      | ✅               | ✅            | ❌            | ✅          |
| macOS    | ✅               | ✅            | ❌            | ✅          |
| watchOS  | ✅               | ✅            | ❌            | ✅          |
| tvOS     | ✅               | ✅            | ❌            | ✅          |
| Linux    | ✅               | ✅            | ❌            | ✅          |
| Windows  | ✅               | ✅            | ❌            | ✅          |
| JS       | ✅               | ✅            | ❌            | ✅          |
| WASM     | ✅               | ✅            | ❌            | ✅          |

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

| Approach               | When     | Rules (approx.) | CI | Real-time | Platform     |
|------------------------|----------|-----------------|----|-----------|--------------|
| **Compiler Plugin**    | Compile  | 14 checkers     | ✅  | ❌         | All (KMP)    |
| **Detekt Rules**       | Analysis | 40 rules        | ✅  | ❌         | All (KMP)    |
| **Android Lint Rules** | Analysis | 35 detectors    | ✅  | ❌         | Android only |
| **IDE Plugin**         | Editing  | 35 inspections  | ❌  | ✅         | All          |
| **Combined (All)**     | All      | 51 patterns     | ✅  | ✅         | -            |
| Code Review            | Manual   | —               | ❌  | ❌         | -            |
| Runtime                | Late     | —               | ❌  | ❌         | -            |

**Notes:**

- Rule codes, severities, and layer matrix: [`docs/rule-codes.yml`](docs/rule-codes.yml)
- Android Lint and IntelliJ include **quick fixes**; IDE adds **intentions**, **gutter icons**, and **Flow Chain Analyzer**

---

## 🛠️ Requirements

- Kotlin 2.3.0+
- K2 compiler (default in Kotlin 2.3+)
- Gradle 8.0+
- Detekt 1.23+ (for detekt-rules)
- IntelliJ IDEA 2024.3+ / Android Studio Ladybug+ (for intellij-plugin)

---

## 📄 License

```
Copyright 2026 Santiago Mattiauda

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

## 📚 Documentation

### Module Documentation

Each module contains its own detailed documentation:

| Module                            | Documentation                                                                                                  | Description                                                             |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------|
| **Gradle Plugin**                 | [gradle-plugin/README.md](gradle-plugin/README.md)                                                             | Installation, configuration, severity settings                          |
| **Detekt Rules**                  | [detekt-rules/README.md](detekt-rules/README.md)                                                               | 40 rules with examples and configuration                                |
| **Android Lint**                  | [lint-rules/README.md](lint-rules/README.md)                                                                   | 35 detectors, Android/Compose-specific detection                        |
| **IntelliJ Plugin**               | [intellij-plugin/README.md](intellij-plugin/README.md)                                                         | 35 inspections, quick fixes, Flow Analyzer, K2 support                  |
| **Annotations**                   | [annotations/README.md](annotations/README.md)                                                                 | @StructuredScope usage and multiplatform support                        |
| **Compiler**                      | [compiler/README.md](compiler/README.md)                                                                       | K2/FIR checker implementation details                                   |
| **Sample (compilation)**          | [compilation/README.md](sample/src/main/kotlin/io/github/santimattius/structured/sample/compilation/README.md) | One example per compiler rule (errors and warnings)                     |
| **Sample (Detekt)**               | [sample-detekt/README.md](sample-detekt/README.md)                                                             | One example per Detekt rule; run `:sample-detekt:detekt` to validate    |
| **Kotlin Coroutines Agent Skill** | [docs/KOTLIN_COROUTINES_SKILL.md](docs/KOTLIN_COROUTINES_SKILL.md)                                             | AI/agent skill and decision resource: 32 practices, strict rules, triage playbook (v2.0.0) |
| **Best Practices**                | [docs/BEST_PRACTICES_COROUTINES.md](docs/BEST_PRACTICES_COROUTINES.md)                                         | Canonical guide to coroutine good/bad practices                         |
| **Decision Guide**                | [docs/DECISION_GUIDE.md](docs/DECISION_GUIDE.md)                                                               | Quick-reference: launch vs async, scope, dispatcher, timeout, Flow      |
| **Suppressing Rules**             | [docs/SUPPRESSING_RULES.md](docs/SUPPRESSING_RULES.md)                                                         | Unified suppression IDs (Compiler, Detekt, Lint, IntelliJ) by rule code |

### Internationalization (i18n)

All user-facing text is externalized for localization:

- **Compiler plugin:** Messages are in
  `compiler/src/main/resources/messages/CompilerBundle*.properties`. **Default language is English**
  so builds and CI are predictable. To use Spanish (or the JVM default locale), set the system
  property:
    - **Spanish:** `-Dstructured.coroutines.compiler.locale=es` (e.g. in `gradle.properties`:
      `org.gradle.jvmargs=... -Dstructured.coroutines.compiler.locale=es`)
    - **JVM default:** `-Dstructured.coroutines.compiler.locale=default`
- **IntelliJ plugin:** Uses `StructuredCoroutinesBundle.properties`; the IDE uses the platform
  language. Spanish: `StructuredCoroutinesBundle_es.properties`.

To add a new language, add a `_<locale>` properties file (e.g. `CompilerBundle_de.properties`) with
the same keys.

### Internationalization (i18n)

All user-facing text is externalized for localization:

- **Compiler plugin:** Messages are in `compiler/src/main/resources/messages/CompilerBundle*.properties`. **Default language is English** so builds and CI are predictable. To use Spanish (or the JVM default locale), set the system property:
  - **Spanish:** `-Dstructured.coroutines.compiler.locale=es` (e.g. in `gradle.properties`: `org.gradle.jvmargs=... -Dstructured.coroutines.compiler.locale=es`)
  - **JVM default:** `-Dstructured.coroutines.compiler.locale=default`
- **IntelliJ plugin:** Uses `StructuredCoroutinesBundle.properties`; the IDE uses the platform language. Spanish: `StructuredCoroutinesBundle_es.properties`.

To add a new language, add a `_<locale>` properties file (e.g. `CompilerBundle_de.properties`) with the same keys.

### External Resources

- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Structured Concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)
- [Detekt Documentation](https://detekt.dev/)
- [Android Lint API](https://googlesamples.github.io/android-custom-lint-rules/)
- [IntelliJ Plugin SDK](https://plugins.jetbrains.com/docs/intellij/welcome.html)
- [K2 Compiler Guide](https://kotlinlang.org/docs/k2-compiler-migration-guide.html)
