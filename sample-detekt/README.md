# sample-detekt

This module contains **intentional violations** of the [structured-coroutines Detekt rules](../../detekt-rules) so you can validate that the rules run and report correctly.

## Purpose

- **Validate** that each Detekt rule implemented in `detekt-rules` fires on the expected code patterns.
- **No compiler plugin** is applied here (unlike `sample/`), so only Detekt performs the analysis.

## How to run

From the project root:

```bash
./gradlew :sample-detekt:detekt
```

You should see **14 findings** from the `structured-coroutines` rule set (the build is configured not to fail; see below).

## Expected findings (14)

| Rule | File | What triggers it |
|------|------|------------------|
| GlobalScopeUsage (SCOPE_001) | GlobalScopeUsageExample.kt | `GlobalScope.launch { }` |
| InlineCoroutineScope (SCOPE_003) | InlineCoroutineScopeExample.kt | `CoroutineScope(Dispatchers.Default).launch { }` |
| RunBlockingInSuspend (RUNBLOCK_002) | RunBlockingInSuspendExample.kt | `runBlocking { }` inside `suspend fun` |
| DispatchersUnconfined (DISPATCH_003) | DispatchersUnconfinedExample.kt | `launch(Dispatchers.Unconfined) { }` |
| CancellationExceptionSubclass (EXCEPT_002) | CancellationExceptionSubclassExample.kt | `class X : CancellationException()` |
| CancellationExceptionSwallowed (CANCEL_003) | CancellationExceptionSwallowedExample.kt | `catch (ex: Exception) { }` inside `launch { }` |
| BlockingCallInCoroutine (DISPATCH_001) | BlockingCallInCoroutineExample.kt | `Thread.sleep()` inside `launch { }` |
| ExternalScopeLaunch (SCOPE_003) | ExternalScopeLaunchExample.kt | `scope.launch { }` from a `suspend fun` |
| LoopWithoutYield (CANCEL_001) | LoopWithoutYieldExample.kt | `for` loop in `suspend fun` without cooperation point |
| RunBlockingWithDelayInTest (TEST_001) | RunBlockingWithDelayInTestExampleTest.kt | `runBlocking { delay() }` in a test file |
| JobInBuilderContext (DISPATCH_004) | JobInBuilderContextExample.kt | `launch(Job()) { }` |
| RedundantLaunchInCoroutineScope (RUNBLOCK_001) | RedundantLaunchInCoroutineScopeExample.kt | single `launch { }` inside `coroutineScope { }` |
| SuspendInFinally (CANCEL_004) | SuspendInFinallyExample.kt | `delay()` in `finally` without `NonCancellable` |
| UnusedDeferred (SCOPE_002) | UnusedDeferredExample.kt | `async { }` result never awaited |

## Configuration

- **detekt.yml** in this directory enables all `structured-coroutines` rules and disables a few default Detekt rules that would also fire on the example code (`TooGenericExceptionCaught`, `MatchingDeclarationName`).
- **ignoreFailures = true** so that `./gradlew build` does not fail on this module; run `:sample-detekt:detekt` to see the report.

## Relation to `sample/`

- **sample**: uses the **compiler plugin**; compilation fails with expected error codes (used by compiler tests).
- **sample-detekt**: uses **Detekt only**; compiles successfully and serves to validate that the Detekt rules report as expected.
