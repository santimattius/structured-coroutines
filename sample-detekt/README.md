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

You should see **53 findings** from the `structured-coroutines` rule set (the build is configured not to fail; see below). Additional default Detekt rules (e.g. `WildcardImport`, `MagicNumber`) may also appear on sample code.

## Expected findings (53 structured-coroutines)

### Core rules (v0.1–v0.5)

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
| JobInBuilderContext (DISPATCH_004) | JobInBuilderContextExample.kt | `launch(Job()) { }` |
| RedundantLaunchInCoroutineScope (RUNBLOCK_001) | RedundantLaunchInCoroutineScopeExample.kt | single `launch { }` inside `coroutineScope { }` |
| SuspendInFinally (CANCEL_004) | SuspendInFinallyExample.kt | `delay()` in `finally` without `NonCancellable` |
| UnusedDeferred (SCOPE_002) | UnusedDeferredExample.kt | `async { }` result never awaited |
| ScopeReuseAfterCancel (CANCEL_005) | ScopeReuseAfterCancelExample.kt | `scope.cancel()` then `scope.launch { }` |
| ChannelNotClosed (CHANNEL_001) | ChannelNotClosedExample.kt, ConsumeEachMultipleConsumersExample.kt | `Channel<Int>()` without `close()` |
| ConsumeEachMultipleConsumers (CHANNEL_002) | ConsumeEachMultipleConsumersExample.kt | same `ch` with `consumeEach` in two `scope.launch { }` |
| FlowBlockingCall (FLOW_001) | FlowBlockingCallExample.kt | `Thread.sleep()` inside `flow { }` |
| RunBlockingWithDelayInTest (TEST_001) | RunBlockingWithDelayInTestExampleTest.kt | `runBlocking { delay() }` in a test file |

### v0.8 — Interop, Flow, KMP, Compose, testing

| Rule | File | What triggers it |
|------|------|------------------|
| SuspendCoroutineWithoutCancellation (INTEROP_001) | SuspendCoroutineWithoutCancellationSample.kt | `suspendCoroutine { }` in suspend function |
| CallbackFlowWithoutAwaitClose (INTEROP_002) | CallbackFlowWithoutAwaitCloseSample.kt | `callbackFlow { }` without `awaitClose` |
| MutableFlowExposed (FLOW_010) | MutableFlowExposedSample.kt, MissingCatchInFlowSample.kt | public `MutableStateFlow` / `MutableSharedFlow` |
| MissingCatchInFlow (FLOW_005) | MissingCatchInFlowSample.kt | Flow chain without `.catch` before terminal |
| SequentialAsyncAwait (CONCUR_003) | SequentialAsyncAwaitSample.kt | inline `async { }.await()` with no parallelism |
| RunBlockingInsteadOfRunTest (TEST_004) | RunBlockingInsteadOfRunTestSampleTest.kt, RunBlockingWithDelayInTestExampleTest.kt | `runBlocking` in `@Test` |

> **KMP-only rules** (`DispatchersIOInCommonMain`, `RunBlockingInCommonMain`) have sample files but require a KMP `commonMain` source set; they do not report in this JVM-only module.

### v0.9 — Concurrency, backend, Flow

| Rule | File | What triggers it |
|------|------|------------------|
| SynchronizedInCoroutine (CONCUR_001) | SynchronizedInCoroutineSample.kt | `synchronized { }` inside coroutine |
| StateInWithEagerlyStrategy (FLOW_006) | StateInEagerlySample.kt | `stateIn(..., SharingStarted.Eagerly, ...)` |
| RedundantWithContext (CONCUR_004) | RedundantWithContextSample.kt | nested `withContext` with same dispatcher |
| SideEffectInMapOperator (FLOW_008) | SideEffectInMapSample.kt | side effect before return in `.map { }` |
| BlockingCallInCoroutineBackend (BACKEND_001) | BlockingCallBackendSample.kt | blocking call in backend coroutine |
| ThreadLocalNotPropagated (BACKEND_002) | ThreadLocalMdcSample.kt | `ThreadLocal` / MDC without `asContextElement` |
| MainScopeWithoutCancel (SCOPE_005) | MainScopeWithoutCancelSample.kt | `MainScope()` without `cancel()` |

> **SharedMutableStateInCoroutine (CONCUR_002)** sample exists but is **info** severity and may be disabled in `detekt.yml` profiles.

### v1.0 — Compose-adjacent, testing, interop, Flow

| Rule | File | What triggers it |
|------|------|------------------|
| HardcodedDispatcherInClass (TEST_005) | HardcodedDispatcherSample.kt, HardcodedDispatcherInClassSample.kt | `Dispatchers.IO` hardcoded in class |
| SharedFlowForOneshotEvents (FLOW_011) | SharedFlowForOneshotEventsSample.kt, SharedFlowOneshotEventsSample.kt, MutableFlowExposedSample.kt | `MutableSharedFlow` for one-shot UI events |
| ChannelFlowVsCallbackFlow (INTEROP_003) | ChannelFlowVsCallbackFlowSample.kt | `channelFlow` for callbacks / `callbackFlow` without `awaitClose` |
| BlockingFutureGet (INTEROP_004) | BlockingFutureGetSample.kt | `CompletableFuture.get()` in `suspend fun` |

## Configuration

- **detekt.yml** in this directory enables all `structured-coroutines` rules and disables a few default Detekt rules that would also fire on the example code (`TooGenericExceptionCaught`, `MatchingDeclarationName`, `UnusedPrivateProperty`).
- **ignoreFailures = true** so that `./gradlew build` does not fail on this module; run `:sample-detekt:detekt` to see the report.

## Relation to `sample/`

- **sample**: uses the **compiler plugin**; compilation fails with expected error codes (used by compiler tests).
- **sample-detekt**: uses **Detekt only**; compiles successfully and serves to validate that the Detekt rules report as expected.
