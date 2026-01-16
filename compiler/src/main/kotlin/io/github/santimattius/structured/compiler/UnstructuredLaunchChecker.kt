package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.fir.references.toResolvedValueParameterSymbol
import org.jetbrains.kotlin.fir.resolve.toClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects unstructured coroutine launches.
 *
 * This checker reports errors when:
 * 1. `launch` or `async` is called on a receiver NOT annotated with @StructuredScope
 * 2. GlobalScope is used as receiver
 * 3. A CoroutineScope is created inline (e.g., CoroutineScope(Dispatchers.IO).launch { })
 */
class UnstructuredLaunchChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        // Target function names
        private val COROUTINE_BUILDER_NAMES = setOf(
            Name.identifier("launch"),
            Name.identifier("async")
        )

        // GlobalScope FQN
        private val GLOBAL_SCOPE_CLASS_ID = ClassId(
            FqName("kotlinx.coroutines"),
            Name.identifier("GlobalScope")
        )

        // @StructuredScope annotation ClassId
        private val STRUCTURED_SCOPE_ANNOTATION_CLASS_ID = ClassId(
            FqName("io.github.santimattius.structured.annotations"),
            Name.identifier("StructuredScope")
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Step 1: Check if this is a call to launch or async
        if (!isCoroutineBuilderCall(expression)) return

        // Step 2: Get the explicit receiver (the scope on which launch/async is called)
        val receiver = expression.explicitReceiver ?: return

        // Step 3: Check for GlobalScope usage
        if (isGlobalScope(receiver, context)) {
            reporter.reportGlobalScopeUsage(expression, context)
            return
        }

        // Step 4: Check for inline CoroutineScope creation
        if (isInlineCoroutineScopeCreation(receiver)) {
            reporter.reportInlineCoroutineScope(expression, context)
            return
        }

        // Step 5: Check if receiver is annotated with @StructuredScope
        if (!isStructuredScope(receiver, context)) {
            reporter.reportUnstructuredLaunch(expression, context)
        }
    }

    /**
     * Checks if the function call is to `launch` or `async`.
     */
    private fun isCoroutineBuilderCall(call: FirFunctionCall): Boolean {
        val calleeReference = call.calleeReference
        val name = calleeReference.name
        return name in COROUTINE_BUILDER_NAMES
    }

    /**
     * Checks if the receiver is GlobalScope.
     */
    private fun isGlobalScope(
        receiver: FirExpression,
        context: CheckerContext
    ): Boolean {
        // Check if it's a direct reference to GlobalScope object
        if (receiver is FirResolvedQualifier) {
            return receiver.classId == GLOBAL_SCOPE_CLASS_ID
        }

        // Check the resolved type
        val classSymbol = receiver.resolvedType.toClassSymbol(context.session) ?: return false
        return classSymbol.classId == GLOBAL_SCOPE_CLASS_ID
    }

    /**
     * Checks if the receiver is an inline CoroutineScope creation.
     * e.g., CoroutineScope(Dispatchers.IO).launch { }
     */
    private fun isInlineCoroutineScopeCreation(receiver: FirExpression): Boolean {
        // If receiver is a function call to CoroutineScope(...), it's inline creation
        if (receiver is FirFunctionCall) {
            val calleeName = receiver.calleeReference.name
            return calleeName == Name.identifier("CoroutineScope")
        }
        return false
    }

    /**
     * Checks if the receiver has the @StructuredScope annotation.
     */
    private fun isStructuredScope(
        receiver: FirExpression,
        context: CheckerContext
    ): Boolean {
        // Case 1: Property access (e.g., scope.launch where scope is a property or parameter)
        if (receiver is FirPropertyAccessExpression) {
            val calleeReference = receiver.calleeReference

            // Check if it's a value parameter (function parameter)
            val parameterSymbol = calleeReference.toResolvedValueParameterSymbol()
            if (parameterSymbol != null) {
                return hasStructuredScopeAnnotation(parameterSymbol, context)
            }

            // Check if it's a property (including properties from constructor parameters)
            val propertySymbol = calleeReference.toResolvedPropertySymbol()
            if (propertySymbol != null) {
                // Check annotation on the property itself
                // Note: For primary constructor val/var properties with annotations,
                // the annotation is available on the property symbol when using
                // @field:, @property:, or default behavior
                return hasStructuredScopeAnnotation(propertySymbol, context)
            }
        }

        return false
    }

    /**
     * Checks if a value parameter symbol has @StructuredScope annotation.
     */
    private fun hasStructuredScopeAnnotation(
        symbol: FirValueParameterSymbol,
        context: CheckerContext
    ): Boolean {
        return symbol.hasAnnotation(STRUCTURED_SCOPE_ANNOTATION_CLASS_ID, context.session)
    }

    /**
     * Checks if a property symbol has @StructuredScope annotation.
     */
    private fun hasStructuredScopeAnnotation(
        symbol: FirPropertySymbol,
        context: CheckerContext
    ): Boolean {
        return symbol.hasAnnotation(STRUCTURED_SCOPE_ANNOTATION_CLASS_ID, context.session)
    }
}
