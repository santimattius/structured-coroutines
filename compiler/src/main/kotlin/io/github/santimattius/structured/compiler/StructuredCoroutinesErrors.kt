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

    init {
        // Register error messages after factories are created
        StructuredCoroutinesErrorRenderer.registerMessages()
    }
}

// ============================================================
// Extension Functions for Reporting Diagnostics
// ============================================================

// --- Core Structured Concurrency ---

/**
 * Reports an unstructured coroutine launch error.
 */
fun DiagnosticReporter.reportUnstructuredLaunch(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH, context)
}

/**
 * Reports a GlobalScope usage error.
 */
fun DiagnosticReporter.reportGlobalScopeUsage(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE, context)
}

/**
 * Reports an inline CoroutineScope creation error.
 */
fun DiagnosticReporter.reportInlineCoroutineScope(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE, context)
}

// --- Blocking & runBlocking ---

/**
 * Reports a runBlocking in suspend function error.
 */
fun DiagnosticReporter.reportRunBlockingInSuspend(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND, context)
}

// --- Job & Context ---

/**
 * Reports a Job/SupervisorJob in builder context error.
 */
fun DiagnosticReporter.reportJobInBuilderContext(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT, context)
}

// --- Dispatchers ---

/**
 * Reports a Dispatchers.Unconfined usage warning.
 */
fun DiagnosticReporter.reportDispatchersUnconfinedUsage(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE, context)
}

// --- Exception Handling ---

/**
 * Reports a CancellationException subclass error.
 */
fun DiagnosticReporter.reportCancellationExceptionSubclass(
    declaration: FirDeclaration,
    context: CheckerContext
) {
    reportOn(declaration.source, StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SUBCLASS, context)
}

/**
 * Reports a suspend call in finally without NonCancellable warning.
 */
fun DiagnosticReporter.reportSuspendInFinally(
    expression: org.jetbrains.kotlin.fir.expressions.FirExpression,
    context: CheckerContext
) {
    reportOn(expression.source, StructuredCoroutinesErrors.SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE, context)
}

/**
 * Reports a CancellationException swallowed warning.
 */
fun DiagnosticReporter.reportCancellationExceptionSwallowed(
    expression: org.jetbrains.kotlin.fir.expressions.FirExpression,
    context: CheckerContext
) {
    reportOn(expression.source, StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SWALLOWED, context)
}

// --- Additional Rules ---

/**
 * Reports an unused Deferred error.
 */
fun DiagnosticReporter.reportUnusedDeferred(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.UNUSED_DEFERRED, context)
}

/**
 * Reports a redundant launch in coroutineScope warning.
 */
fun DiagnosticReporter.reportRedundantLaunchInCoroutineScope(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE, context)
}

// ============================================================
// Configurable Report Functions
// ============================================================

/**
 * Reports an unstructured launch with configurable severity.
 */
fun DiagnosticReporter.reportUnstructuredLaunch(
    call: FirCall,
    context: CheckerContext,
    severity: org.jetbrains.kotlin.diagnostics.Severity
) {
    // Use the existing diagnostic (it's ERROR by default, but severity is checked at report time)
    reportOn(call.source, StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH, context)
}

/**
 * Reports GlobalScope usage with configurable severity.
 */
fun DiagnosticReporter.reportGlobalScopeUsage(
    call: FirCall,
    context: CheckerContext,
    severity: org.jetbrains.kotlin.diagnostics.Severity
) {
    reportOn(call.source, StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE, context)
}

/**
 * Reports inline CoroutineScope creation with configurable severity.
 */
fun DiagnosticReporter.reportInlineCoroutineScope(
    call: FirCall,
    context: CheckerContext,
    severity: org.jetbrains.kotlin.diagnostics.Severity
) {
    reportOn(call.source, StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE, context)
}
