package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects `runBlocking` calls inside suspend functions.
 *
 * From Best Practices (2.2):
 * "Calling runBlocking from code already based on coroutines (especially inside suspend functions)
 * blocks the current thread, breaks the non-blocking model, and can cause deadlocks or ANRs."
 *
 * Recommended: Use runBlocking only as a bridge from purely blocking code to coroutines.
 */
class RunBlockingInSuspendChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        private val RUN_BLOCKING_NAME = Name.identifier("runBlocking")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is a call to runBlocking
        if (!isRunBlockingCall(expression)) return

        // Check if we're inside a suspend function
        if (isInsideSuspendFunction(context)) {
            reporter.reportRunBlockingInSuspend(expression, context)
        }
    }

    /**
     * Checks if the function call is to `runBlocking`.
     */
    private fun isRunBlockingCall(call: FirFunctionCall): Boolean {
        return call.calleeReference.name == RUN_BLOCKING_NAME
    }

    /**
     * Checks if we're currently inside a suspend function.
     */
    private fun isInsideSuspendFunction(context: CheckerContext): Boolean {
        // Walk up the containing declarations to find if we're in a suspend function
        for (declaration in context.containingDeclarations) {
            if (declaration is FirSimpleFunction) {
                // Check the status for suspend modifier
                if (declaration.status.isSuspend) {
                    return true
                }
            }
        }
        return false
    }
}
