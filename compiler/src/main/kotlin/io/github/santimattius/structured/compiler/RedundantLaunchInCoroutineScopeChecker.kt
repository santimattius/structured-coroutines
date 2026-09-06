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
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirDoWhileLoop
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirWhileLoop
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects redundant `launch` calls inside `coroutineScope`.
 *
 * ## Problem (Best Practice 2.1)
 *
 * Using `launch` on the last line of a `coroutineScope` is redundant because
 * `coroutineScope` already waits for all children to complete. If you want the
 * function to wait, execute the work directly without wrapping it in `launch`.
 *
 * ```kotlin
 * // ⚠️ WARNING: Redundant launch
 * suspend fun bad() = coroutineScope {
 *     launch { work() }  // Innecesario - debería ser solo work()
 * }
 *
 * // ✅ GOOD: Direct execution
 * suspend fun good() = coroutineScope {
 *     work()  // Directo, sin launch
 * }
 *
 * // ✅ GOOD: Multiple launches (not redundant)
 * suspend fun good() = coroutineScope {
 *     launch { work1() }
 *     launch { work2() }
 * }
 *
 * // ✅ GOOD: launch with async (not redundant)
 * suspend fun good() = coroutineScope {
 *     launch { work1() }
 *     async { work2() }
 * }
 * ```
 *
 * ## Detection Logic
 *
 * 1. Identifies `coroutineScope` calls
 * 2. Analyzes the lambda body to count `launch` calls
 * 3. If there's exactly 1 `launch` and no other builders → WARNING
 * 4. If there are multiple builders → OK (not redundant)
 *
 * ## Limitations
 *
 * - Only analyzes the direct block (not nested blocks)
 * - May have false positives in complex expressions
 * - Doesn't detect cases where launch is in conditional expressions
 *
 * @see StructuredCoroutinesErrors.REDUNDANT_LAUNCH_IN_COROUTINE_SCOPE
 */
class RedundantLaunchInCoroutineScopeChecker(
    private val config: PluginConfiguration,
) : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        private val COROUTINE_SCOPE_NAME = Name.identifier("coroutineScope")
        private val LAUNCH_NAME = Name.identifier("launch")
        private val ASYNC_NAME = Name.identifier("async")
        private val ITERATION_METHODS = setOf(
            Name.identifier("forEach"),
            Name.identifier("onEach"),
            Name.identifier("map"),
            Name.identifier("mapNotNull"),
            Name.identifier("flatMap"),
            Name.identifier("filter"),
            Name.identifier("filterNotNull")
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        if (expression.calleeReference.name != COROUTINE_SCOPE_NAME) return

        val lambdaBlock = getLambdaBlock(expression) ?: return
        val result = countBuildersAndRepeating(lambdaBlock, insideRepeating = false)

        if (result.launchCount == 1 && result.launchCount + result.asyncCount == 1 && !result.singleLaunchInsideRepeating) {
            reporter.reportRedundantLaunchInCoroutineScope(expression, context, config)
        }
    }

    /**
     * Gets the lambda block from the coroutineScope call.
     *
     * Mirrors the verified precedent in `CallbackFlowWithoutAwaitCloseChecker.getTrailingLambdaBlock`:
     * a trailing lambda argument is wrapped as a [FirAnonymousFunctionExpression] whose
     * `anonymousFunction.body` is the actual [FirBlock] — casting the argument directly to
     * [FirBlock] (the previous implementation) always yields null.
     *
     * @param call The coroutineScope call
     * @return The block expression, or null if not found
     */
    private fun getLambdaBlock(call: FirFunctionCall): FirBlock? {
        // Get the last argument (the trailing lambda)
        val lambdaArg = call.argumentList.arguments.lastOrNull() ?: return null

        (lambdaArg as? FirBlock)?.let { return it }
        return (lambdaArg as? FirAnonymousFunctionExpression)?.anonymousFunction?.body as? FirBlock
    }

    /**
     * Counts launch/async and whether the single launch (if any) is inside forEach/for/while.
     */
    private fun countBuildersAndRepeating(block: FirBlock, insideRepeating: Boolean): BuilderCountResult {
        var launchCount = 0
        var asyncCount = 0
        var singleLaunchInsideRepeating = false

        for (statement in block.statements) {
            val r = countBuildersInStatement(statement, insideRepeating)
            launchCount += r.launchCount
            asyncCount += r.asyncCount
            if (r.singleLaunchInsideRepeating) singleLaunchInsideRepeating = true
        }

        return BuilderCountResult(launchCount, asyncCount, singleLaunchInsideRepeating)
    }

    private data class BuilderCountResult(
        val launchCount: Int,
        val asyncCount: Int,
        val singleLaunchInsideRepeating: Boolean
    )

    private fun countBuildersInStatement(statement: FirStatement, insideRepeating: Boolean): BuilderCountResult {
        when (statement) {
            is FirFunctionCall -> {
                val name = statement.calleeReference.name
                if (name == LAUNCH_NAME) {
                    return BuilderCountResult(1, 0, insideRepeating)
                }
                if (name == ASYNC_NAME) {
                    return BuilderCountResult(0, 1, false)
                }
                val iterating = name in ITERATION_METHODS
                var launchCount = 0
                var asyncCount = 0
                var singleLaunchInsideRepeating = false
                for (argument in statement.argumentList.arguments) {
                    if (argument is FirBlock) {
                        val r = countBuildersAndRepeating(argument, insideRepeating = insideRepeating || iterating)
                        launchCount += r.launchCount
                        asyncCount += r.asyncCount
                        if (r.singleLaunchInsideRepeating) singleLaunchInsideRepeating = true
                    }
                }
                return BuilderCountResult(launchCount, asyncCount, singleLaunchInsideRepeating)
            }
            is FirBlock -> return countBuildersAndRepeating(statement, insideRepeating)
            is FirReturnExpression -> {
                // The last statement of a lambda body is desugared into an implicit
                // `return <lastExpression>` (FirReturnExpression) wrapping that expression, e.g.
                // in `coroutineScope { launch { } }` the block's only statement is this wrapper,
                // not a bare FirFunctionCall — so a trailing launch/async must be unwrapped here
                // or it silently drops out of the count (and, worse, throws off the counting of
                // any builder before it, since a truthful count requires this to still be seen).
                return countBuildersInStatement(statement.result, insideRepeating)
            }
            is FirWhileLoop -> {
                val body = statement.block ?: return BuilderCountResult(0, 0, false)
                return countBuildersAndRepeating(body, insideRepeating = true)
            }
            is FirDoWhileLoop -> {
                val body = statement.block ?: return BuilderCountResult(0, 0, false)
                return countBuildersAndRepeating(body, insideRepeating = true)
            }
            else -> return BuilderCountResult(0, 0, false)
        }
    }
}
