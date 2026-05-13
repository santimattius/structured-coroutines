/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.name.Name

/**
 * FIR check for [INTEROP_001]: `suspendCoroutine { }` in suspend functions —
 * cancellation is not propagated; prefer `suspendCancellableCoroutine`.
 */
class SuspendCoroutineWithoutCancellationChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        private val SUSPEND_COROUTINE = Name.identifier("suspendCoroutine")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val name = expression.calleeReference.name
        if (name != SUSPEND_COROUTINE) return
        if (!isInsideSuspendFunction(context)) return

        reporter.reportSuspendCoroutineWithoutCancellation(expression, context)
    }

    @OptIn(SymbolInternals::class)
    private fun isInsideSuspendFunction(context: CheckerContext): Boolean {
        for (element in context.containingDeclarations) {
            val declaration = element.fir
            if (declaration is FirNamedFunction && declaration.status.isSuspend) {
                return true
            }
        }
        return false
    }
}
