package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects usage of Dispatchers.Unconfined.
 *
 * From Best Practices (3.2):
 * "Using Dispatchers.Unconfined in production for 'avoiding thread switches'.
 * The code runs in whatever thread resumes it, making it hard to reason about execution
 * and can end up running on the UI thread with blocking calls."
 *
 * Recommended:
 * - Reserve Dispatchers.Unconfined for very special cases or legacy testing
 * - In production, always choose an appropriate dispatcher: Default, Main, IO, etc.
 *
 * Note: This emits a WARNING (not error) since Unconfined has valid use cases in testing.
 */
class DispatchersUnconfinedChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        // Coroutine builders and context switchers
        private val CONTEXT_USING_FUNCTIONS = setOf(
            Name.identifier("launch"),
            Name.identifier("async"),
            Name.identifier("withContext")
        )

        private val DISPATCHERS_NAME = Name.identifier("Dispatchers")
        private val UNCONFINED_NAME = Name.identifier("Unconfined")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is a coroutine builder or withContext call
        val calleeName = expression.calleeReference.name
        if (calleeName !in CONTEXT_USING_FUNCTIONS) return

        // Check arguments for Dispatchers.Unconfined
        for (argument in expression.arguments) {
            if (isDispatchersUnconfined(argument)) {
                reporter.reportDispatchersUnconfinedUsage(expression, context)
                return
            }
        }
    }

    /**
     * Checks if an expression is Dispatchers.Unconfined
     */
    private fun isDispatchersUnconfined(expression: org.jetbrains.kotlin.fir.expressions.FirExpression): Boolean {
        // Check for property access like Dispatchers.Unconfined
        if (expression is FirPropertyAccessExpression) {
            val propertyName = expression.calleeReference.name
            if (propertyName == UNCONFINED_NAME) {
                // Check if the receiver is Dispatchers
                val receiver = (expression as? FirQualifiedAccessExpression)?.explicitReceiver
                if (receiver is FirPropertyAccessExpression) {
                    return receiver.calleeReference.name == DISPATCHERS_NAME
                }
            }
        }
        return false
    }
}
