/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.view

import io.github.santimattius.structured.intellij.inspections.AsyncWithoutAwaitInspection
import io.github.santimattius.structured.intellij.inspections.CallbackFlowWithoutAwaitCloseInspection
import io.github.santimattius.structured.intellij.inspections.CancellationExceptionSubclassInspection
import io.github.santimattius.structured.intellij.inspections.CancellationExceptionSwallowedInspection
import io.github.santimattius.structured.intellij.inspections.CollectAsStateWithoutLifecycleInspection
import io.github.santimattius.structured.intellij.inspections.DispatchersIOInCommonMainInspection
import io.github.santimattius.structured.intellij.inspections.DispatchersUnconfinedInspection
import io.github.santimattius.structured.intellij.inspections.GlobalScopeInspection
import io.github.santimattius.structured.intellij.inspections.InlineCoroutineScopeInspection
import io.github.santimattius.structured.intellij.inspections.JobInBuilderContextInspection
import io.github.santimattius.structured.intellij.inspections.LifecycleAwareFlowCollectionInspection
import io.github.santimattius.structured.intellij.inspections.LoopWithoutYieldInspection
import io.github.santimattius.structured.intellij.inspections.MainDispatcherMisuseInspection
import io.github.santimattius.structured.intellij.inspections.MissingCatchInFlowInspection
import io.github.santimattius.structured.intellij.inspections.MutableFlowExposedInspection
import io.github.santimattius.structured.intellij.inspections.RunBlockingInSuspendInspection
import io.github.santimattius.structured.intellij.inspections.RunBlockingInsteadOfRunTestInspection
import io.github.santimattius.structured.intellij.inspections.ScopeReuseAfterCancelInspection
import io.github.santimattius.structured.intellij.inspections.LaunchInWithUnstructuredScopeInspection
import io.github.santimattius.structured.intellij.inspections.SequentialAsyncAwaitInspection
import io.github.santimattius.structured.intellij.inspections.StateInWithEagerlyStrategyInspection
import io.github.santimattius.structured.intellij.inspections.SynchronizedInCoroutineInspection
import io.github.santimattius.structured.intellij.inspections.SuspendCoroutineWithoutCancellationInspection
import io.github.santimattius.structured.intellij.inspections.SuspendInFinallyInspection
import io.github.santimattius.structured.intellij.inspections.UnstructuredLaunchInspection
import io.github.santimattius.structured.intellij.inspections.WithTimeoutScopeCancellationInspection

/**
 * Registry that maps each inspection class to its "What to do" action summary
 * and the URL of the corresponding section in the best-practices guide.
 *
 * Used by the Structured Coroutines tool window to show actionable guidance
 * per finding, satisfying the "Qué hacer" requirement from the iteration plan.
 */
object InspectionGuideRegistry {

    private const val BASE_URL =
        "https://github.com/santimattius/structured-coroutines/blob/main/docs/BEST_PRACTICES_COROUTINES.md"

    /**
     * Encapsulates the actionable guidance for a single inspection finding.
     *
     * @property whatToDo Short action summary (1–2 lines) shown in the tool window.
     * @property guideUrl URL to the relevant section in BEST_PRACTICES_COROUTINES.md.
     */
    data class GuideEntry(val whatToDo: String, val guideUrl: String)

    private val registry: Map<Class<*>, GuideEntry> = mapOf(
        GlobalScopeInspection::class.java to GuideEntry(
            whatToDo = "Replace with viewModelScope, lifecycleScope, or coroutineScope/supervisorScope. Never use GlobalScope in production.",
            guideUrl = "$BASE_URL#11-scope_001--using-globalscope-in-production-code"
        ),
        MainDispatcherMisuseInspection::class.java to GuideEntry(
            whatToDo = "Wrap the blocking call with withContext(Dispatchers.IO). Never block Dispatchers.Main.",
            guideUrl = "$BASE_URL#31-dispatch_001--mixing-blocking-code-with-wrong-dispatchers"
        ),
        ScopeReuseAfterCancelInspection::class.java to GuideEntry(
            whatToDo = "Use cancelChildren() instead of cancel() to keep the scope alive. A cancelled Job does not accept new children.",
            guideUrl = "$BASE_URL#45-cancel_005--reusing-a-cancelled-coroutinescope"
        ),
        RunBlockingInSuspendInspection::class.java to GuideEntry(
            whatToDo = "Remove runBlocking. Call the suspend function directly or use coroutineScope { } to structure concurrent work.",
            guideUrl = "$BASE_URL#22-runblock_002--using-runblocking-inside-suspend-functions"
        ),
        UnstructuredLaunchInspection::class.java to GuideEntry(
            whatToDo = "Launch from a structured scope: viewModelScope, lifecycleScope, or coroutineScope { }.",
            guideUrl = "$BASE_URL#13-scope_003--breaking-structured-concurrency"
        ),
        AsyncWithoutAwaitInspection::class.java to GuideEntry(
            whatToDo = "Call .await() on the Deferred, or replace async { } with launch { } if no result is needed.",
            guideUrl = "$BASE_URL#12-scope_002--using-async-without-calling-await"
        ),
        InlineCoroutineScopeInspection::class.java to GuideEntry(
            whatToDo = "Inject a scope from outside or use coroutineScope { } / supervisorScope { } instead of creating one inline.",
            guideUrl = "$BASE_URL#13-scope_003--breaking-structured-concurrency"
        ),
        JobInBuilderContextInspection::class.java to GuideEntry(
            whatToDo = "Remove Job()/SupervisorJob() from the builder context. Use supervisorScope { } for independent child failure isolation.",
            guideUrl = "$BASE_URL#34-dispatch_004--passing-job-directly-as-context-to-builders"
        ),
        SuspendInFinallyInspection::class.java to GuideEntry(
            whatToDo = "Wrap the suspend call with withContext(NonCancellable) { } to ensure it completes even if the coroutine is cancelled.",
            guideUrl = "$BASE_URL#44-cancel_004--suspendable-cleanup-without-noncancellable"
        ),
        CancellationExceptionSwallowedInspection::class.java to GuideEntry(
            whatToDo = "Catch CancellationException separately and rethrow it. Never swallow it in a generic catch(Exception).",
            guideUrl = "$BASE_URL#43-cancel_003--swallowing-cancellationexception"
        ),
        CancellationExceptionSubclassInspection::class.java to GuideEntry(
            whatToDo = "Use Exception or RuntimeException for domain errors. Never subclass CancellationException.",
            guideUrl = "$BASE_URL#52-except_002--extending-cancellationexception-for-domain-errors"
        ),
        DispatchersUnconfinedInspection::class.java to GuideEntry(
            whatToDo = "Use Dispatchers.Default or Dispatchers.IO in production. Reserve Unconfined for specific test scenarios.",
            guideUrl = "$BASE_URL#33-dispatch_003--abusing-dispatchersunconfined"
        ),
        LoopWithoutYieldInspection::class.java to GuideEntry(
            whatToDo = "Add ensureActive(), yield(), or delay() inside the loop body to allow cancellation.",
            guideUrl = "$BASE_URL#41-cancel_001--ignoring-cancellation-in-intensive-loops"
        ),
        LifecycleAwareFlowCollectionInspection::class.java to GuideEntry(
            whatToDo = "Use repeatOnLifecycle(Lifecycle.State.STARTED) or flowWithLifecycle() so collection stops when the UI goes to background.",
            guideUrl = "$BASE_URL#82-lifecycle-aware-flow-collection-android"
        ),
        WithTimeoutScopeCancellationInspection::class.java to GuideEntry(
            whatToDo = "Replace withTimeout with withTimeoutOrNull to get null on timeout without cancelling the parent scope. Or catch TimeoutCancellationException explicitly.",
            guideUrl = "$BASE_URL#46-cancel_006--withtimeout-and-scope-cancellation"
        ),
        CollectAsStateWithoutLifecycleInspection::class.java to GuideEntry(
            whatToDo = "Use collectAsStateWithLifecycle() from lifecycle-runtime-compose so collection pauses when the lifecycle is not at least STARTED.",
            guideUrl = "$BASE_URL#83-compose_001--collectasstate-without-lifecycle-awareness"
        ),
        RunBlockingInsteadOfRunTestInspection::class.java to GuideEntry(
            whatToDo = "Replace runBlocking with runTest from kotlinx-coroutines-test for virtual time and structured TestScope.",
            guideUrl = "$BASE_URL#64-test_004--runblocking-instead-of-runtest"
        ),
        DispatchersIOInCommonMainInspection::class.java to GuideEntry(
            whatToDo = "Inject CoroutineDispatcher from platform code or use expect/actual — Dispatchers.IO is not available in Kotlin/Common on Native/JS.",
            guideUrl = "$BASE_URL#111-kmp_001--dispatchersio-in-commonmain"
        ),
        SuspendCoroutineWithoutCancellationInspection::class.java to GuideEntry(
            whatToDo = "Replace suspendCoroutine with suspendCancellableCoroutine and use invokeOnCancellation for cleanup.",
            guideUrl = "$BASE_URL#101-interop_001--wrapping-callbacks-without-cancellation-support"
        ),
        CallbackFlowWithoutAwaitCloseInspection::class.java to GuideEntry(
            whatToDo = "Add awaitClose { } inside callbackFlow to unregister listeners when the collector leaves.",
            guideUrl = "$BASE_URL#102-interop_002--callbackflow-without-awaitclose"
        ),
        MutableFlowExposedInspection::class.java to GuideEntry(
            whatToDo = "Use a private MutableStateFlow/MutableSharedFlow with a public read-only asStateFlow()/asSharedFlow() property.",
            guideUrl = "$BASE_URL#95-flow_010--mutablestateflow-exposed"
        ),
        MissingCatchInFlowInspection::class.java to GuideEntry(
            whatToDo = "Add .catch { } before collect/collectLatest/launchIn so upstream operator failures are handled.",
            guideUrl = "$BASE_URL#96-flow_005--missing-catch-in-flow-chain"
        ),
        SequentialAsyncAwaitInspection::class.java to GuideEntry(
            whatToDo = "Use withContext(coroutineContext) { } for sequential work, or start multiple async calls without awaiting between them.",
            guideUrl = "$BASE_URL#15-concur_003--sequential-asyncawait"
        ),
        SynchronizedInCoroutineInspection::class.java to GuideEntry(
            whatToDo = "Replace synchronized { } with Mutex.withLock { } so the coroutine suspends instead of blocking the dispatcher thread.",
            guideUrl = "$BASE_URL#121-concur_001--synchronizedincoroutine"
        ),
        StateInWithEagerlyStrategyInspection::class.java to GuideEntry(
            whatToDo = "Use SharingStarted.WhileSubscribed(5_000) so upstream collection runs only while collectors are active (plus rotation buffer).",
            guideUrl = "$BASE_URL#97-flow_006--stateinwitheagerlystrategy"
        ),
        LaunchInWithUnstructuredScopeInspection::class.java to GuideEntry(
            whatToDo = "Pass a structured scope to launchIn: viewModelScope, lifecycleScope, or a scope from coroutineScope { }.",
            guideUrl = "$BASE_URL#98-flow_007--launchinwithunstructuredscope"
        ),
    )

    /** Returns the [GuideEntry] for the given inspection class, or null if not registered. */
    fun getGuide(inspectionClass: Class<*>): GuideEntry? = registry[inspectionClass]
}
