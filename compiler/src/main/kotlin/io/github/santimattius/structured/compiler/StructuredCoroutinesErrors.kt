@file:Suppress("ClassName", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

/**
 * Copyright 2024 Santiago Mattiauda
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
 * This factory provides human-readable error messages for all diagnostics
 * emitted by the Structured Coroutines compiler plugin.
 *
 * Must be defined before [StructuredCoroutinesErrors] to avoid initialization issues.
 */
object StructuredCoroutinesErrorRenderer : BaseDiagnosticRendererFactory() {
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    override val MAP = KtDiagnosticFactoryToRendererMap("StructuredCoroutines")

    /**
     * Registers all error messages. Called from [StructuredCoroutinesErrors.init].
     */
    internal fun registerMessages() {
        // === Core Structured Concurrency Rules ===
        MAP.put(
            StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH,
            "Unstructured coroutine launch detected. Use one of the following structured alternatives:\n" +
                "  • Framework scopes: viewModelScope, lifecycleScope, rememberCoroutineScope()\n" +
                "  • Annotated scopes: @StructuredScope on your CoroutineScope parameter or property\n" +
                "  • Structured builders: coroutineScope { }, supervisorScope { }"
        )
        MAP.put(
            StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE,
            "GlobalScope usage is not allowed. GlobalScope bypasses structured concurrency and can lead to " +
                "resource leaks. Use one of the following alternatives:\n" +
                "  • Framework scopes: viewModelScope, lifecycleScope, rememberCoroutineScope()\n" +
                "  • Annotated scopes: @StructuredScope on your CoroutineScope\n" +
                "  • Structured builders: coroutineScope { }, supervisorScope { }"
        )
        MAP.put(
            StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE,
            "Inline CoroutineScope creation is not allowed. Creating CoroutineScope(Dispatchers.X).launch { } " +
                "creates an orphan coroutine without lifecycle management. Use one of the following:\n" +
                "  • Framework scopes: viewModelScope, lifecycleScope, rememberCoroutineScope()\n" +
                "  • Annotated scopes: @StructuredScope on a properly managed CoroutineScope\n" +
                "  • Structured builders: coroutineScope { }, supervisorScope { }"
        )

        // === runBlocking & Blocking Rules ===
        MAP.put(
            StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND,
            "runBlocking should not be called inside a suspend function. It blocks the current thread and " +
                "defeats the purpose of coroutines. Use suspending alternatives or withContext instead."
        )

        // === Job & Context Rules ===
        MAP.put(
            StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT,
            "Passing Job() or SupervisorJob() directly to launch/async/withContext breaks structured concurrency. " +
                "The new Job becomes an independent parent, breaking the parent-child relationship. " +
                "Use supervisorScope { } instead, or define a proper CoroutineScope with SupervisorJob."
        )

        // === Dispatcher Rules ===
        MAP.put(
            StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE,
            "Dispatchers.Unconfined should be avoided in production code. It runs coroutines in whatever thread " +
                "resumes them, making execution unpredictable. Use Dispatchers.Default, Dispatchers.IO, or Dispatchers.Main instead."
        )

        // === Exception Handling Rules ===
        MAP.put(
            StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SUBCLASS,
            "Extending CancellationException for domain errors is not allowed. CancellationException has special " +
                "semantics in coroutines - it doesn't propagate like normal exceptions and only cancels the current " +
                "coroutine and its children. Use a regular Exception or RuntimeException for domain errors."
        )
        MAP.put(
            StructuredCoroutinesErrors.SUSPEND_IN_FINALLY_WITHOUT_NON_CANCELLABLE,
            "Suspend call in finally block without NonCancellable context. If the coroutine is cancelled, " +
                "any suspend call will throw CancellationException and cleanup may not execute. " +
                "Wrap critical cleanup in withContext(NonCancellable) { }."
        )
        MAP.put(
            StructuredCoroutinesErrors.CANCELLATION_EXCEPTION_SWALLOWED,
            "catch(Exception) or catch(Throwable) may swallow CancellationException, preventing proper cancellation. " +
                "Either add a separate catch(CancellationException) { throw it } clause, use ensureActive() in the " +
                "catch block, or re-throw the exception."
        )
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
