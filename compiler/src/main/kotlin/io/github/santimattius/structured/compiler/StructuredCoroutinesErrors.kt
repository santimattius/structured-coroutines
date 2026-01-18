@file:Suppress("ClassName", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactory0
import org.jetbrains.kotlin.diagnostics.KtDiagnosticFactoryToRendererMap
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.SourceElementPositioningStrategies
import org.jetbrains.kotlin.diagnostics.rendering.BaseDiagnosticRendererFactory
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.expressions.FirCall
import org.jetbrains.kotlin.psi.KtElement

/**
 * Renderer factory for structured coroutines error messages.
 * Must be defined before StructuredCoroutinesErrors to avoid initialization issues.
 */
object StructuredCoroutinesErrorRenderer : BaseDiagnosticRendererFactory() {
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    override val MAP = KtDiagnosticFactoryToRendererMap("StructuredCoroutines")

    // Messages will be added after factory registration in StructuredCoroutinesErrors
    internal fun registerMessages() {
        // === Existing rules ===
        MAP.put(
            StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH,
            "Unstructured coroutine launch detected. Use a CoroutineScope annotated with @StructuredScope, " +
                "or use structured concurrency patterns like coroutineScope { } or supervisorScope { }."
        )
        MAP.put(
            StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE,
            "GlobalScope usage is not allowed. GlobalScope bypasses structured concurrency and can lead to " +
                "resource leaks. Use a CoroutineScope annotated with @StructuredScope instead."
        )
        MAP.put(
            StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE,
            "Inline CoroutineScope creation is not allowed. Creating a CoroutineScope inline (e.g., " +
                "CoroutineScope(Dispatchers.IO).launch { }) bypasses structured concurrency. " +
                "Use a CoroutineScope annotated with @StructuredScope instead."
        )

        // === New rules from best practices ===
        MAP.put(
            StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND,
            "runBlocking should not be called inside a suspend function. It blocks the current thread and " +
                "defeats the purpose of coroutines. Use suspending alternatives or withContext instead."
        )
        MAP.put(
            StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT,
            "Passing Job() or SupervisorJob() directly to launch/async/withContext breaks structured concurrency. " +
                "The new Job becomes an independent parent, breaking the parent-child relationship. " +
                "Use supervisorScope { } instead, or define a proper CoroutineScope with SupervisorJob."
        )
        MAP.put(
            StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE,
            "Dispatchers.Unconfined should be avoided in production code. It runs coroutines in whatever thread " +
                "resumes them, making execution unpredictable. Use Dispatchers.Default, Dispatchers.IO, or Dispatchers.Main instead."
        )
    }
}

/**
 * Diagnostic definitions for structured coroutines errors.
 *
 * Rules enforced:
 * 1. No GlobalScope usage
 * 2. No inline CoroutineScope creation
 * 3. launch/async must use @StructuredScope annotated scopes
 * 4. No runBlocking inside suspend functions
 * 5. No Job()/SupervisorJob() passed directly to builders
 * 6. No Dispatchers.Unconfined in production code
 */
object StructuredCoroutinesErrors {

    // === Existing rules ===

    val UNSTRUCTURED_COROUTINE_LAUNCH: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "UNSTRUCTURED_COROUTINE_LAUNCH",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    val GLOBAL_SCOPE_USAGE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "GLOBAL_SCOPE_USAGE",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    val INLINE_COROUTINE_SCOPE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "INLINE_COROUTINE_SCOPE",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    // === New rules from best practices ===

    val RUN_BLOCKING_IN_SUSPEND: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "RUN_BLOCKING_IN_SUSPEND",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    val JOB_IN_BUILDER_CONTEXT: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "JOB_IN_BUILDER_CONTEXT",
        severity = Severity.ERROR,
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    val DISPATCHERS_UNCONFINED_USAGE: KtDiagnosticFactory0 = KtDiagnosticFactory0(
        name = "DISPATCHERS_UNCONFINED_USAGE",
        severity = Severity.WARNING, // Warning instead of error - might have valid use cases in tests
        defaultPositioningStrategy = SourceElementPositioningStrategies.CALL_ELEMENT_WITH_DOT,
        psiType = KtElement::class,
        rendererFactory = StructuredCoroutinesErrorRenderer
    )

    init {
        // Register error messages after factories are created
        StructuredCoroutinesErrorRenderer.registerMessages()
    }
}

// === Extension functions for reporting ===

fun DiagnosticReporter.reportUnstructuredLaunch(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH, context)
}

fun DiagnosticReporter.reportGlobalScopeUsage(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE, context)
}

fun DiagnosticReporter.reportInlineCoroutineScope(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE, context)
}

fun DiagnosticReporter.reportRunBlockingInSuspend(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND, context)
}

fun DiagnosticReporter.reportJobInBuilderContext(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT, context)
}

fun DiagnosticReporter.reportDispatchersUnconfinedUsage(call: FirCall, context: CheckerContext) {
    reportOn(call.source, StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE, context)
}
