# Compiler Plugin for Structured Coroutines

K2/FIR Kotlin Compiler Plugin that enforces structured concurrency at compile time.

## Overview

This module contains the Kotlin Compiler Plugin implementation using the K2/FIR (Frontend Intermediate Representation) API. It performs compile-time analysis to detect violations of structured concurrency best practices.

## Architecture

```
compiler/
├── StructuredCoroutinesCompilerPluginRegistrar.kt  # Plugin registration
├── ScoroutinesFirExtensionRegistrar.kt             # FIR extension registration
├── ScoroutinesCallCheckerExtension.kt              # Call expression analysis
├── PluginConfiguration.kt                          # Severity configuration
├── StructuredCoroutinesErrors.kt                   # Error/warning definitions
└── Checkers/
    ├── UnstructuredLaunchChecker.kt                # GlobalScope, InlineScope, Unstructured
    ├── RunBlockingInSuspendChecker.kt              # runBlocking in suspend
    ├── JobInBuilderContextChecker.kt               # Job()/SupervisorJob() in builders
    ├── DispatchersUnconfinedChecker.kt             # Dispatchers.Unconfined
    ├── CancellationExceptionSubclassChecker.kt     # Extending CancellationException
    ├── SuspendInFinallyChecker.kt                  # Suspend in finally
    ├── CancellationExceptionSwallowedChecker.kt    # catch(Exception) swallowing
    ├── UnusedDeferredChecker.kt                    # async without await
    └── RedundantLaunchInCoroutineScopeChecker.kt   # Redundant launch
```

## Checkers (11 Rules)

| Checker | Rule | Default Severity |
|---------|------|------------------|
| `UnstructuredLaunchChecker` | GlobalScope usage | Error |
| `UnstructuredLaunchChecker` | Inline CoroutineScope | Error |
| `UnstructuredLaunchChecker` | Unstructured launch | Error |
| `RunBlockingInSuspendChecker` | runBlocking in suspend | Error |
| `JobInBuilderContextChecker` | Job()/SupervisorJob() in builders | Error |
| `DispatchersUnconfinedChecker` | Dispatchers.Unconfined | Warning |
| `CancellationExceptionSubclassChecker` | Extending CancellationException | Error |
| `SuspendInFinallyChecker` | Suspend in finally | Warning |
| `CancellationExceptionSwallowedChecker` | catch(Exception) swallowing | Warning |
| `UnusedDeferredChecker` | async without await | Error |
| `RedundantLaunchInCoroutineScopeChecker` | Redundant launch | Warning |

## Configuration

All rules support configurable severity via the Gradle Plugin:

```kotlin
structuredCoroutines {
    globalScopeUsage.set("error")     // or "warning"
    dispatchersUnconfined.set("warning")
    // ... other rules
}
```

## Requirements

- Kotlin 2.3.0+ (K2 compiler)
- Gradle 8.0+

## Building

```bash
./gradlew :compiler:build
./gradlew :compiler:test
```

## License

```
Copyright 2026 Santiago Mattiauda
Licensed under the Apache License, Version 2.0
```
