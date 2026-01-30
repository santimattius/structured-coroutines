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
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects unused Deferred values from async calls.
 *
 * ## Problem (Best Practice 1.2)
 *
 * Using `async` without calling `await()` is confusing and can hide exceptions
 * that remain hanging inside the Deferred. If you don't need a result, use `launch` instead.
 *
 * ```kotlin
 * // ❌ ERROR: async without await
 * val deferred = scope.async { computeValue() }
 * // deferred never used - exception may be hidden
 *
 * // ✅ GOOD: async with await
 * val deferred = scope.async { computeValue() }
 * val result = deferred.await()
 *
 * // ✅ GOOD: async with awaitAll
 * val deferred1 = scope.async { computeValue1() }
 * val deferred2 = scope.async { computeValue2() }
 * val results = awaitAll(deferred1, deferred2)
 *
 * // ✅ GOOD: Use launch if no result needed
 * scope.launch { doWork() }  // No result needed
 * ```
 *
 * ## Detection Logic
 *
 * 1. Identifies `async` calls that are assigned to variables
 * 2. Analyzes the containing block for `.await()` calls on that variable
 * 3. Reports error if no `.await()` is found in the same block
 *
 * ## Limitations
 *
 * - Only detects cases in the same block (doesn't cross function boundaries)
 * - Doesn't detect cases where Deferred is passed as parameter
 * - Doesn't detect complex expressions using the Deferred
 *
 * @see StructuredCoroutinesErrors.UNUSED_DEFERRED
 */
class UnusedDeferredChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * Name of the async function to detect.
         */
        private val ASYNC_NAME = Name.identifier("async")

        /**
         * Name of the await function to detect.
         */
        private val AWAIT_NAME = Name.identifier("await")

        /**
         * Name of the awaitAll function to detect.
         */
        private val AWAIT_ALL_NAME = Name.identifier("awaitAll")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is an async call
        if (expression.calleeReference.name != ASYNC_NAME) return

        // Find the containing block
        val containingBlock = findContainingBlock(expression, context) ?: return

        // Check if this async is part of a variable assignment
        // We'll look for the variable in the same statement
        val variableName = findVariableNameFromStatement(expression, containingBlock, context) ?: return

        // Check if await() is called on this variable in the same block
        if (!hasAwaitCall(containingBlock, variableName, context)) {
            reporter.reportUnusedDeferred(expression, context)
        }
    }

    /**
     * Finds the variable name if the async call is assigned to a variable in the same statement.
     *
     * @param expression The async call expression
     * @param block The containing block
     * @param context The checker context
     * @return The variable name if found, null otherwise
     */
    private fun findVariableNameFromStatement(
        expression: FirFunctionCall,
        block: FirBlock,
        context: CheckerContext
    ): Name? {
        // Look for the statement containing this expression
        for (statement in block.statements) {
            if (statement is FirVariable) {
                // Check if the initializer is our async call
                val initializer = statement.initializer
                if (initializer == expression) {
                    return statement.name
                }
            }
        }
        return null
    }

    /**
     * Finds the containing block for the expression.
     *
     * Uses the context to find the function body.
     *
     * @param expression The expression to find the block for
     * @param context The checker context
     * @return The containing block, or null if not found
     */
    private fun findContainingBlock(
        expression: FirExpression,
        context: CheckerContext
    ): FirBlock? {
        // Get the function body from context
        for (declaration in context.containingDeclarations) {
            if (declaration is FirSimpleFunction) {
                val body = declaration.body
                if (body is FirBlock) {
                    return body
                }
            }
        }
        return null
    }

    /**
     * Checks if there's an await() call on the given variable in the block.
     *
     * @param block The block to search
     * @param variableName The name of the variable to check
     * @param context The checker context
     * @return true if await() is found, false otherwise
     */
    private fun hasAwaitCall(
        block: FirBlock,
        variableName: Name,
        context: CheckerContext
    ): Boolean {
        for (statement in block.statements) {
            if (containsAwaitCall(statement, variableName, context)) {
                return true
            }
        }
        return false
    }

    /**
     * Recursively checks if a statement contains an await() call on the variable.
     *
     * @param statement The statement to check
     * @param variableName The variable name to look for
     * @param context The checker context
     * @return true if await() is found
     */
    private fun containsAwaitCall(
        statement: FirStatement,
        variableName: Name,
        context: CheckerContext
    ): Boolean {
        when (statement) {
            is FirFunctionCall -> {
                // Check if this is await() or awaitAll()
                val calleeName = statement.calleeReference.name
                if (calleeName == AWAIT_NAME || calleeName == AWAIT_ALL_NAME) {
                    // Check if the receiver is our variable
                    val receiver = statement.explicitReceiver
                    if (receiver is FirPropertyAccessExpression) {
                        val receiverName = receiver.calleeReference.name
                        if (receiverName == variableName) {
                            return true
                        }
                    }
                }

                // Recursively check arguments (for awaitAll with multiple deferreds)
                for (argument in statement.argumentList.arguments) {
                    if (argument is FirPropertyAccessExpression) {
                        val argName = argument.calleeReference.name
                        if (argName == variableName) {
                            return true
                        }
                    }
                    if (argument is FirBlock) {
                        if (hasAwaitCall(argument, variableName, context)) {
                            return true
                        }
                    }
                }
            }
            is FirBlock -> {
                for (innerStatement in statement.statements) {
                    if (containsAwaitCall(innerStatement, variableName, context)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
