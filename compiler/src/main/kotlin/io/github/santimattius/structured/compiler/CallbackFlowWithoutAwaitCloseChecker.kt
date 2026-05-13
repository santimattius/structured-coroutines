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
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.name.Name

/**
 * FIR check for [INTEROP_002]: `callbackFlow { }` must call `awaitClose { }`
 * inside the lambda (kotlinx.coroutines API contract).
 *
 * Does not apply to `channelFlow`.
 */
class CallbackFlowWithoutAwaitCloseChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        private val CALLBACK_FLOW = Name.identifier("callbackFlow")
        private val CHANNEL_FLOW = Name.identifier("channelFlow")
        private val AWAIT_CLOSE = Name.identifier("awaitClose")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        val callee = expression.calleeReference.name
        if (callee == CHANNEL_FLOW) return
        if (callee != CALLBACK_FLOW) return

        val lambdaBlock = getTrailingLambdaBlock(expression) ?: return

        if (blockContainsAwaitClose(lambdaBlock)) return

        reporter.reportCallbackFlowWithoutAwaitClose(expression, context)
    }

    /** Trailing lambda of `callbackFlow { }`: [FirBlock] or body of anonymous function. */
    private fun getTrailingLambdaBlock(call: FirFunctionCall): FirBlock? {
        val last = call.argumentList.arguments.lastOrNull() ?: return null
        (last as? FirBlock)?.let { return it }
        return (last as? FirAnonymousFunctionExpression)?.anonymousFunction?.body as? FirBlock
    }

    private fun blockContainsAwaitClose(block: FirBlock): Boolean =
        block.statements.any { statementContainsAwaitClose(it) }

    private fun statementContainsAwaitClose(statement: FirStatement): Boolean {
        when (statement) {
            is FirFunctionCall -> {
                if (statement.calleeReference.name == AWAIT_CLOSE) return true
                return statement.argumentList.arguments.any { arg ->
                    subtreeMayContainAwaitClose(arg)
                }
            }
            is FirBlock -> return blockContainsAwaitClose(statement)
            else -> {
                return false
            }
        }
    }

    private fun subtreeMayContainAwaitClose(expr: FirExpression): Boolean {
        return when (expr) {
            is FirFunctionCall -> {
                if (expr.calleeReference.name == AWAIT_CLOSE) return true
                expr.argumentList.arguments.any { subtreeMayContainAwaitClose(it) }
            }
            is FirBlock -> blockContainsAwaitClose(expr)
            is FirAnonymousFunctionExpression ->
                expr.anonymousFunction.body?.let { body ->
                    (body as? FirBlock)?.let { blockContainsAwaitClose(it) } == true
                } == true
            else -> false
        }
    }
}
