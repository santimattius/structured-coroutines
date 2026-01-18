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
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirTryExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.Name

/**
 * FIR Try Expression Checker that detects suspend calls in `finally` blocks
 * without `withContext(NonCancellable)`.
 *
 * ## Problem (Best Practice 4.3)
 *
 * In a `finally` block, making suspend calls without wrapping them in
 * `withContext(NonCancellable)` is dangerous:
 *
 * ```kotlin
 * // ❌ BAD: Suspend call in finally without NonCancellable
 * try {
 *     doWork()
 * } finally {
 *     saveToDb()  // May not execute if coroutine is cancelled!
 * }
 * ```
 *
 * If the coroutine is in *cancelling* state, any suspension will throw
 * `CancellationException` again and the cleanup may not execute.
 *
 * ## Recommended Practice
 *
 * For critical cleanup that needs to suspend, use `withContext(NonCancellable)`:
 *
 * ```kotlin
 * // ✅ GOOD: Wrapped in NonCancellable
 * try {
 *     doWork()
 * } finally {
 *     withContext(NonCancellable) {
 *         saveToDb()  // Will execute even if cancelled
 *         closeResources()
 *     }
 * }
 * ```
 *
 * ## Detection
 *
 * This checker:
 * 1. Identifies `try` expressions with `finally` blocks
 * 2. Scans the finally block for suspend function calls
 * 3. Verifies if suspend calls are properly wrapped in `withContext(NonCancellable)`
 * 4. Reports a warning for unprotected suspend calls
 *
 * @see <a href="https://kotlinlang.org/docs/cancellation-and-timeouts.html#run-non-cancellable-block">Non-cancellable block</a>
 */
class SuspendInFinallyChecker : FirTryExpressionChecker(MppCheckerKind.Common) {

    companion object {
        private val WITH_CONTEXT_NAME = Name.identifier("withContext")
        private val NON_CANCELLABLE_NAME = Name.identifier("NonCancellable")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirTryExpression) {
        val finallyBlock = expression.finallyBlock ?: return

        // Check if there are suspend calls not wrapped in withContext(NonCancellable)
        val unprotectedSuspendCalls = findUnprotectedSuspendCalls(finallyBlock, context)
        
        if (unprotectedSuspendCalls.isNotEmpty()) {
            // Report on the first unprotected suspend call found
            reporter.reportSuspendInFinally(expression, context)
        }
    }

    /**
     * Finds suspend function calls in a finally block that are not wrapped
     * in withContext(NonCancellable).
     */
    private fun findUnprotectedSuspendCalls(
        block: FirBlock,
        context: CheckerContext,
        insideNonCancellable: Boolean = false
    ): List<FirFunctionCall> {
        val unprotectedCalls = mutableListOf<FirFunctionCall>()

        for (statement in block.statements) {
            collectUnprotectedSuspendCalls(statement, context, insideNonCancellable, unprotectedCalls)
        }

        return unprotectedCalls
    }

    /**
     * Recursively collects unprotected suspend calls from statements.
     */
    private fun collectUnprotectedSuspendCalls(
        statement: FirStatement,
        context: CheckerContext,
        insideNonCancellable: Boolean,
        result: MutableList<FirFunctionCall>
    ) {
        when (statement) {
            is FirFunctionCall -> {
                // Check if this is withContext(NonCancellable)
                if (isWithContextNonCancellable(statement)) {
                    // Everything inside is protected, don't report
                    return
                }

                // Check if this is a suspend call
                if (!insideNonCancellable && isSuspendCall(statement, context)) {
                    result.add(statement)
                }

                // Recursively check arguments (lambdas passed to functions)
                for (argument in statement.argumentList.arguments) {
                    if (argument is FirBlock) {
                        result.addAll(findUnprotectedSuspendCalls(argument, context, insideNonCancellable))
                    }
                }
            }
            is FirBlock -> {
                for (innerStatement in statement.statements) {
                    collectUnprotectedSuspendCalls(innerStatement, context, insideNonCancellable, result)
                }
            }
        }
    }

    /**
     * Checks if a function call is `withContext(NonCancellable)`.
     */
    private fun isWithContextNonCancellable(call: FirFunctionCall): Boolean {
        if (call.calleeReference.name != WITH_CONTEXT_NAME) return false

        // Check if first argument is NonCancellable
        val firstArg = call.argumentList.arguments.firstOrNull() ?: return false
        
        if (firstArg is org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression) {
            return firstArg.calleeReference.name == NON_CANCELLABLE_NAME
        }
        
        return false
    }

    /**
     * Checks if a function call is to a suspend function.
     */
    private fun isSuspendCall(call: FirFunctionCall, context: CheckerContext): Boolean {
        val symbol = call.calleeReference.toResolvedCallableSymbol() as? FirNamedFunctionSymbol
            ?: return false
        return symbol.resolvedStatus.isSuspend
    }
}
