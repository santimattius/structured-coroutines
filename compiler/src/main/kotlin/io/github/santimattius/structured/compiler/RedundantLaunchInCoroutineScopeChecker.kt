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
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement
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
class RedundantLaunchInCoroutineScopeChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * Name of the coroutineScope function to detect.
         */
        private val COROUTINE_SCOPE_NAME = Name.identifier("coroutineScope")

        /**
         * Name of the launch function to detect.
         */
        private val LAUNCH_NAME = Name.identifier("launch")

        /**
         * Name of the async function to detect.
         */
        private val ASYNC_NAME = Name.identifier("async")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is a coroutineScope call
        if (expression.calleeReference.name != COROUTINE_SCOPE_NAME) return

        // Get the lambda block (first argument)
        val lambdaBlock = getLambdaBlock(expression) ?: return

        // Count launch and async calls in the block
        val launchCount = countBuilderCalls(lambdaBlock, LAUNCH_NAME)
        val asyncCount = countBuilderCalls(lambdaBlock, ASYNC_NAME)
        val totalBuilders = launchCount + asyncCount

        // If there's exactly 1 launch and no other builders, it's redundant
        if (launchCount == 1 && totalBuilders == 1) {
            reporter.reportRedundantLaunchInCoroutineScope(expression, context)
        }
    }

    /**
     * Gets the lambda block from the coroutineScope call.
     *
     * @param call The coroutineScope call
     * @return The block expression, or null if not found
     */
    private fun getLambdaBlock(call: FirFunctionCall): FirBlock? {
        // The lambda is typically the last argument
        val arguments = call.argumentList.arguments
        if (arguments.isEmpty()) return null

        // Get the last argument (the lambda)
        val lambdaArg = arguments.lastOrNull() ?: return null

        // The lambda body is a FirBlock
        return lambdaArg as? FirBlock
    }

    /**
     * Counts calls to a specific builder (launch or async) in a block.
     *
     * @param block The block to analyze
     * @param builderName The name of the builder to count
     * @return The count of builder calls
     */
    private fun countBuilderCalls(block: FirBlock, builderName: Name): Int {
        var count = 0
        for (statement in block.statements) {
            count += countBuilderCallsInStatement(statement, builderName)
        }
        return count
    }

    /**
     * Recursively counts builder calls in a statement.
     *
     * @param statement The statement to analyze
     * @param builderName The name of the builder to count
     * @return The count of builder calls
     */
    private fun countBuilderCallsInStatement(statement: FirStatement, builderName: Name): Int {
        when (statement) {
            is FirFunctionCall -> {
                // Check if this is the builder we're looking for
                if (statement.calleeReference.name == builderName) {
                    return 1
                }
                // Recursively check arguments (lambdas)
                var count = 0
                for (argument in statement.argumentList.arguments) {
                    if (argument is FirBlock) {
                        count += countBuilderCalls(argument, builderName)
                    }
                }
                return count
            }
            is FirBlock -> {
                var count = 0
                for (innerStatement in statement.statements) {
                    count += countBuilderCallsInStatement(innerStatement, builderName)
                }
                return count
            }
            else -> return 0
        }
    }
}
