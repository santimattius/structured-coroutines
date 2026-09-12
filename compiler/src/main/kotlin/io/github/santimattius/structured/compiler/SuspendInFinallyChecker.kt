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
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirTryExpressionChecker
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirResolvedQualifier
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirVariableSymbol
import org.jetbrains.kotlin.fir.types.ConeClassLikeType
import org.jetbrains.kotlin.fir.types.resolvedType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
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
class SuspendInFinallyChecker(
    private val config: PluginConfiguration,
) : FirTryExpressionChecker(MppCheckerKind.Common) {

    companion object {
        private val WITH_CONTEXT_NAME = Name.identifier("withContext")
        private val PLUS_NAME = Name.identifier("plus")
        private val NON_CANCELLABLE_CLASS_ID =
            ClassId(FqName("kotlinx.coroutines"), Name.identifier("NonCancellable"))
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirTryExpression) {
        val finallyBlock = expression.finallyBlock ?: return

        // Check if there are suspend calls not wrapped in withContext(NonCancellable)
        val unprotectedSuspendCalls = findUnprotectedSuspendCalls(finallyBlock, context)
        
        if (unprotectedSuspendCalls.isNotEmpty()) {
            // Report on the first unprotected suspend call found
            reporter.reportSuspendInFinally(expression, context, config)
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
     * Checks if a function call is `withContext(NonCancellable)` (or a `NonCancellable`-derived
     * combination such as `withContext(NonCancellable + Dispatchers.IO)`).
     */
    private fun isWithContextNonCancellable(call: FirFunctionCall): Boolean {
        if (call.calleeReference.name != WITH_CONTEXT_NAME) return false

        val firstArg = call.argumentList.arguments.firstOrNull() ?: return false
        return isNonCancellable(firstArg)
    }

    /**
     * Checks if [expression] resolves to `kotlinx.coroutines.NonCancellable`, whether referenced
     * as a bare imported name, fully qualified, combined via `+` with another
     * `CoroutineContext`, through a variable typed as `NonCancellable`, or through a `val`
     * declared with a wider static type (e.g. `CoroutineContext`) but initialized to
     * `NonCancellable` (#90 — the pre-#91 crash workaround for the `FirResolvedQualifier.classId`
     * removal, still in use by call sites that predate the fix).
     *
     * A resolved object reference is a [FirResolvedQualifier], resolved via the shared
     * [resolvedClassId] helper (not a direct `.classId` call — removed as a member in Kotlin
     * 2.4.20, KT-84522). [ConeClassLikeType.lookupTag] is used for the fallback path instead of
     * the unstable `ConeKotlinType.toClassSymbol()` API that changed signature in Kotlin 2.3.20.
     * The alias case unwraps one level through [FirVariableSymbol.resolvedInitializer] — which
     * forces resolution to `BODY_RESOLVE` internally instead of reading raw (possibly unresolved)
     * FIR, avoiding the `@SymbolInternals` opt-in that a direct `.fir` access would require —
     * and recurses so the alias's initializer is checked by the same rules. Restricted to `val`
     * (immutable) bindings: a `var`'s initializer is not necessarily its value at the call site,
     * so trusting it for a mutable binding would silently accept a reassignment away from
     * `NonCancellable` — the same false-negative risk `#69` tightened against.
     */
    private fun isNonCancellable(expression: FirExpression): Boolean {
        if (expression is FirResolvedQualifier) {
            return expression.resolvedClassId() == NON_CANCELLABLE_CLASS_ID
        }

        if (expression is FirFunctionCall && expression.calleeReference.name == PLUS_NAME) {
            val receiver = expression.explicitReceiver
            if (receiver != null && isNonCancellable(receiver)) return true
            return expression.argumentList.arguments.any { isNonCancellable(it) }
        }

        val classId = (expression.resolvedType as? ConeClassLikeType)?.lookupTag?.classId
        if (classId == NON_CANCELLABLE_CLASS_ID) return true

        if (expression is FirPropertyAccessExpression) {
            val variableSymbol = expression.calleeReference.toResolvedCallableSymbol() as? FirVariableSymbol<*>
            if (variableSymbol?.isVal == true) {
                val initializer = variableSymbol.resolvedInitializer
                if (initializer != null) return isNonCancellable(initializer)
            }
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
