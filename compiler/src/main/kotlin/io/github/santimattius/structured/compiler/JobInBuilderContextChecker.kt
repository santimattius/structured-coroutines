package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects Job() or SupervisorJob() passed directly to coroutine builders.
 *
 * From Best Practices (3.3 & 5.1):
 * "Doing `launch(Job()) { ... }` or `withContext(SupervisorJob()) { ... }` breaks the parent-child
 * relationship and with it structured concurrency: the new Job becomes an independent parent
 * and the caller loses control over its children."
 *
 * Recommended:
 * - Let builders use the Job from the current scope
 * - Use `supervisorScope { }` for supervisor semantics
 * - Or define a proper CoroutineScope with SupervisorJob for multiple independent coroutines
 */
class JobInBuilderContextChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        // Coroutine builders that accept CoroutineContext
        private val COROUTINE_BUILDERS = setOf(
            Name.identifier("launch"),
            Name.identifier("async"),
            Name.identifier("withContext")
        )

        // Job constructors to detect
        private val JOB_CONSTRUCTORS = setOf(
            Name.identifier("Job"),
            Name.identifier("SupervisorJob")
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is a coroutine builder call
        val calleeName = expression.calleeReference.name
        if (calleeName !in COROUTINE_BUILDERS) return

        // Check arguments for Job() or SupervisorJob() calls
        for (argument in expression.arguments) {
            if (argument is FirFunctionCall) {
                val argCalleeName = argument.calleeReference.name
                if (argCalleeName in JOB_CONSTRUCTORS) {
                    reporter.reportJobInBuilderContext(expression, context)
                    return
                }
            }
        }
    }
}
