@file:Suppress("ClassName", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.psi.KtElement

/**
 * Renderer factory for structured coroutines error messages.
 *
 * Messages are loaded from [CompilerMessages] (messages.CompilerBundle) for i18n.
 * Each message includes a rule code (e.g. [SCOPE_001]) and a link to BEST_PRACTICES.
 *
 * Must be defined before [StructuredCoroutinesErrors] to avoid initialization issues.
 */
object StructuredCoroutinesErrorRenderer : BaseDiagnosticRendererFactory() {
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    override val MAP = KtDiagnosticFactoryToRendererMap("StructuredCoroutines")

    /**
     * Registers all error messages from the compiler message bundle. Called from [StructuredCoroutinesErrors.init].
     */
    internal fun registerMessages() {
        MAP.put(StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH, CompilerMessages.message("UNSTRUCTURED_COROUTINE_LAUNCH"))
        MAP.put(StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE, CompilerMessages.message("GLOBAL_SCOPE_USAGE"))
        MAP.put(StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE, CompilerMessages.message("INLINE_COROUTINE_SCOPE"))
        MAP.put(StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND, CompilerMessages.message("RUN_BLOCKING_IN_SUSPEND"))
        MAP.put(StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT, CompilerMessages.message("JOB_IN_BUILDER_CONTEXT"))
        MAP.put(StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE, CompilerMessages.message("DISPATCHERS_UNCONFINED_USAGE"))
        MAP.put(StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SUBCLASS, CompilerMessages.message("CANCELLATION_EXCEPTION_SUBCLASS"))
        MAP.put(StructuredCoroutinesErrors.SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE, CompilerMessages.message("SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE"))
        MAP.put(StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SWALLOWED, CompilerMessages.message("CANCELLATION_EXCEPTION_SWALLOWED"))
        MAP.put(StructuredCoroutinesErrors.UNUSED_DEFERRED, CompilerMessages.message("UNUSED_DEFERRED"))
        MAP.put(StructuredCoroutinesErrors.REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE, CompilerMessages.message("REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE"))
        MAP.put(StructuredCoroutinesErrors.LOOP_WITHOUT_YIELD, CompilerMessages.message("LOOP_WITHOUT_YIELD"))
        MAP.put(StructuredCoroutinesErrors.SUSPEND_COROUTINE_WITHOUT_CANCELLATION, CompilerMessages.message("SUSPEND_COROUTINE_WITHOUT_CANCELLATION"))
        MAP.put(StructuredCoroutinesErrors.CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE, CompilerMessages.message("CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE"))
    }
}

/**
 * Diagnostic definitions for structured coroutines errors.
 *
 * This object contains all diagnostic factories used by the Structured Coroutines
 * compiler plugin. Each diagnostic represents a violation of structured concurrency
 * best practices.
 *
 * ## Rules Enforced
 *
 * ### Core Structured Concurrency (v0)
 * 1. [UNSTRUCTURED_COROUTINE_LAUNCH] - launch/async must use @StructuredScope scopes
 * 2. [GLOBAL_SCOPE_USAGE] - No GlobalScope usage
 * 3. [INLINE_COROUTINE_SCOPE] - No inline CoroutineScope creation
 *
 * ### Blocking & runBlocking (Best Practice 2.x)
 * 4. [RUN_BLOCKING_IN_SUSPEND] - No runBlocking inside suspend functions
 *
 * ### Job & Context (Best Practice 3.x, 5.1)
 * 5. [JOB_IN_BUILDER_CONTEXT] - No Job()/SupervisorJob() passed directly to builders
 *
 * ### Dispatchers (Best Practice 3.2)
 * 6. [DISPATCHERS_UNCONFINED_USAGE] - Warn about Dispatchers.Unconfined usage
 *
 * ### Exception Handling (Best Practice 4.x, 5.2)
 * 7. [CANCELLATION_EXCEPTION_SUBCLASS] - No extending CancellationException
 * 8. [SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE] - Suspend calls in finally need NonCancellable
 * 9. [CANCELLATION_EXCEPTION_SWALLOWED] - catch(Exception) must handle CancellationException
 */
object StructuredCoroutinesErrors {

    // ============================================================
    // Core Structured Concurrency Rules
    // ============================================================

    /**
     * Error when launch/async is called on a scope not annotated with @StructuredScope.
     */
    val UNSTRUCTURED_COROUTINE_LAUNCH: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "UNSTRUCTURED_COROUTINE_LAUNCH",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    /**
     * Error when GlobalScope is used as a coroutine scope.
     */
    val GLOBAL_SCOPE_USAGE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "GLOBAL_SCOPE_USAGE",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    /**
     * Error when CoroutineScope is created inline (e.g., CoroutineScope(Dispatchers.IO).launch).
     */
    val INLINE_COROUTINE_SCOPE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "INLINE_COROUTINE_SCOPE",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    // ============================================================
    // Blocking & runBlocking Rules
    // ============================================================

    /**
     * Error when runBlocking is called inside a suspend function.
     */
    val RUN_BLOCKING_IN_SUSPEND: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "RUN_BLOCKING_IN_SUSPEND",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    // ============================================================
    // Job & Context Rules
    // ============================================================

    /**
     * Error when Job() or SupervisorJob() is passed directly to coroutine builders.
     */
    val JOB_IN_BUILDER_CONTEXT: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "JOB_IN_BUILDER_CONTEXT",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    // ============================================================
    // Dispatcher Rules
    // ============================================================

    /**
     * Warning when Dispatchers.Unconfined is used.
     * Note: This is a WARNING, not an ERROR, because Unconfined has valid use cases in tests.
     */
    val DISPATCHERS_UNCONFINED_USAGE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "DISPATCHERS_UNCONFINED_USAGE",
        severity = Severity.WARNING,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    // ============================================================
    // Exception Handling Rules
    // ============================================================

    /**
     * Error when a class extends CancellationException for domain errors.
     */
    val CANCELLATION_EXCEPTION_SUBCLASS: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "CANCELLATION_EXCEPTION_SUBCLASS",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.DECLARATION_NAME,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    /**
     * Warning when suspend calls in finally block are not wrapped in NonCancellable.
     * Note: This is a WARNING because there might be legitimate cases where it's acceptable.
     */
    val SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE",
        severity = Severity.WARNING,
        defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    /**
     * Warning when catch(Exception) may swallow CancellationException.
     * Note: This is a WARNING because static analysis can't always determine intent.
     */
    val CANCELLATION_EXCEPTION_SWALLOWED: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "CANCELLATION_EXCEPTION_SWALLOWED",
        severity = Severity.WARNING,
        defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    // ============================================================
    // Additional Rules (Best Practice 1.2, 2.1)
    // ============================================================

    /**
     * Error when async is called but the Deferred result is never awaited.
     * Note: This is an ERROR because unused Deferred can hide exceptions.
     */
    val UNUSED_DEFERRED: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "UNUSED_DEFERRED",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    /**
     * Warning when coroutineScope contains only a single launch, which is redundant.
     * Note: This is a WARNING because it can be intentional in some cases.
     */
    val REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE",
        severity = Severity.WARNING,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    // ============================================================
    // Cancellation & Loops (Best Practice 4.1)
    // ============================================================

    /**
     * Warning when a for/while loop in a suspend function has no cooperation point.
     * The coroutine cannot be cancelled until the loop completes.
     */
    val LOOP_WITHOUT_YIELD: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "LOOP_WITHOUT_YIELD",
        severity = Severity.WARNING,
        defaultPositioningStrategy = SourceElementPositioningStrategies.DEFAULT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    /** INTEROP_001 — `suspendCoroutine` does not propagate cancellation (use suspendCancellableCoroutine). */
    val SUSPEND_COROUTINE_WITHOUT_CANCELLATION: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "SUSPEND_COROUTINE_WITHOUT_CANCELLATION",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    /** INTEROP_002 — `callbackFlow` must include `awaitClose` for lifecycle cleanup. */
    val CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    init {
        // Register error messages after factories are created
        StructuredCoroutinesErrorRenderer.registerMessages()
    }
}

// ============================================================
// Extension Functions for Reporting Diagnostics (#68, ADR-5)
// ============================================================
//
// Every function below takes the injected [PluginConfiguration] and delegates to
// [PluginConfiguration.report], which short-circuits when the rule's effective severity is
// [RuleSeverity.DISABLED]. The 3 previously-dead `(call, context, severity: Severity)` overloads
// that existed here (zero call sites) are gone: severity is now always *resolved*, never
// caller-supplied, and `Severity` cannot represent "disabled" anyway (ADR-2).

// --- Core Structured Concurrency ---

/**
 * Reports an unstructured coroutine launch, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportUnstructuredLaunch(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.UNSTRUCTURED_LAUNCH, StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH, call.source, context)
}

/**
 * Reports a GlobalScope usage, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportGlobalScopeUsage(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.GLOBAL_SCOPE_USAGE, StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE, call.source, context)
}

/**
 * Reports an inline CoroutineScope creation, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportInlineCoroutineScope(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.INLINE_COROUTINE_SCOPE, StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE, call.source, context)
}

// --- Blocking & runBlocking ---

/**
 * Reports a runBlocking in suspend function, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportRunBlockingInSuspend(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.RUN_BLOCKING_IN_SUSPEND, StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND, call.source, context)
}

// --- Job & Context ---

/**
 * Reports a Job/SupervisorJob in builder context, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportJobInBuilderContext(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.JOB_IN_BUILDER_CONTEXT, StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT, call.source, context)
}

// --- Dispatchers ---

/**
 * Reports a Dispatchers.Unconfined usage, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportDispatchersUnconfinedUsage(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.DISPATCHERS_UNCONFINED, StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE, call.source, context)
}

// --- Exception Handling ---

/**
 * Reports a CancellationException subclass, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportCancellationExceptionSubclass(
    declaration: FirDeclaration,
    context: CheckerContext,
    config: PluginConfiguration,
) {
    config.report(this, ScoroutinesRule.CANCELLATION_EXCEPTION_SUBCLASS, StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SUBCLASS, declaration.source, context)
}

/**
 * Reports a suspend call in finally without NonCancellable, unless [config] resolves the rule to
 * disabled.
 */
fun DiagnosticReporter.reportSuspendInFinally(
    expression: org.jetbrains.kotlin.fir.expressions.FirExpression,
    context: CheckerContext,
    config: PluginConfiguration,
) {
    config.report(this, ScoroutinesRule.SUSPEND_IN_FINALLY, StructuredCoroutinesErrors.SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE, expression.source, context)
}

/**
 * Reports a CancellationException swallowed, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportCancellationExceptionSwallowed(
    expression: org.jetbrains.kotlin.fir.expressions.FirExpression,
    context: CheckerContext,
    config: PluginConfiguration,
) {
    config.report(this, ScoroutinesRule.CANCELLATION_EXCEPTION_SWALLOWED, StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SWALLOWED, expression.source, context)
}

// --- Additional Rules ---

/**
 * Reports an unused Deferred, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportUnusedDeferred(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.UNUSED_DEFERRED, StructuredCoroutinesErrors.UNUSED_DEFERRED, call.source, context)
}

/**
 * Reports a redundant launch in coroutineScope, unless [config] resolves the rule to disabled.
 */
fun DiagnosticReporter.reportRedundantLaunchInCoroutineScope(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE, StructuredCoroutinesErrors.REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE, call.source, context)
}

/**
 * Reports a loop in suspend function without cooperation point, unless [config] resolves the
 * rule to disabled. Takes a raw [org.jetbrains.kotlin.KtSourceElement] (the loop's own source,
 * not a [org.jetbrains.kotlin.fir.expressions.FirExpression]) because [LoopWithoutYieldChecker]
 * reports on the loop statement itself, not on an expression.
 */
fun DiagnosticReporter.reportLoopWithoutYield(
    source: org.jetbrains.kotlin.KtSourceElement?,
    context: CheckerContext,
    config: PluginConfiguration,
) {
    config.report(this, ScoroutinesRule.LOOP_WITHOUT_YIELD, StructuredCoroutinesErrors.LOOP_WITHOUT_YIELD, source, context)
}

/** INTEROP_001 — reports suspendCoroutine usage in suspend contexts, unless [config] disables it. */
fun DiagnosticReporter.reportSuspendCoroutineWithoutCancellation(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.SUSPEND_COROUTINE_WITHOUT_CANCELLATION, StructuredCoroutinesErrors.SUSPEND_COROUTINE_WITHOUT_CANCELLATION, call.source, context)
}

/** INTEROP_002 — reports callbackFlow without awaitClose, unless [config] disables it. */
fun DiagnosticReporter.reportCallbackFlowWithoutAwaitClose(call: FirCall, context: CheckerContext, config: PluginConfiguration) {
    config.report(this, ScoroutinesRule.CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE, StructuredCoroutinesErrors.CALLBACK_FLOW_WITHOUT_AWAIT_CLOSE, call.source, context)
}
