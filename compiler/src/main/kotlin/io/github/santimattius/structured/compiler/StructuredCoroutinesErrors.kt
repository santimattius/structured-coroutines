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
    }
}

/**
 * Diagnostic definitions for unstructured coroutine launch errors.
 *
 * This error is reported when:
 * - `launch` or `async` is called on a CoroutineScope that is NOT annotated with @StructuredScope
 * - GlobalScope is used
 * - A CoroutineScope is created inline (e.g., CoroutineScope(Dispatchers.IO).launch { })
 */
object StructuredCoroutinesErrors {

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

    init {
        // Register error messages after factories are created
        StructuredCoroutinesErrorRenderer.registerMessages()
    }
}

/**
 * Extension function to report unstructured launch error.
 */
fun DiagnosticReporter.reportUnstructuredLaunch(
    call: FirCall,
    context: CheckerContext
) {
    reportOn(
        call.source,
        StructuredCoroutinesErrors.UNSTRUCTURED_COROUTINE_LAUNCH,
        context
    )
}

/**
 * Extension function to report GlobalScope usage error.
 */
fun DiagnosticReporter.reportGlobalScopeUsage(
    call: FirCall,
    context: CheckerContext
) {
    reportOn(
        call.source,
        StructuredCoroutinesErrors.GLOBAL_SCOPE_USAGE,
        context
    )
}

/**
 * Extension function to report inline CoroutineScope creation error.
 */
fun DiagnosticReporter.reportInlineCoroutineScope(
    call: FirCall,
    context: CheckerContext
) {
    reportOn(
        call.source,
        StructuredCoroutinesErrors.INLINE_COROUTINE_SCOPE,
        context
    )
}
