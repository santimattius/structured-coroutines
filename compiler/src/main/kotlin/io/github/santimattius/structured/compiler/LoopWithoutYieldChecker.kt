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
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirDoWhileLoop
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirStatement
import org.jetbrains.kotlin.fir.expressions.FirWhileLoop
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.Name

/**
 * FIR checker that detects loops in suspend functions without cooperation points.
 *
 * ## Problem (Best Practice 4.1)
 *
 * Long-running loops in suspend functions without cooperation points (yield, ensureActive,
 * delay) cannot be cancelled until the loop completes.
 *
 * ## Detection
 *
 * Visits each suspend function body, finds while/do-while (and for) loops, and reports
 * LOOP_WITHOUT_YIELD if the loop body has no cooperation point.
 */
class LoopWithoutYieldChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {

    companion object {
        private val COOPERATION_POINT_NAMES = setOf(
            Name.identifier("yield"),
            Name.identifier("ensureActive"),
            Name.identifier("delay"),
            Name.identifier("suspendCancellableCoroutine"),
            Name.identifier("withTimeout"),
            Name.identifier("withTimeoutOrNull")
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(declaration: FirSimpleFunction) {
        if (!declaration.status.isSuspend) return
        val body = declaration.body ?: return
        checkBlockForLoopsWithoutYield(body, context, reporter)
    }

    private fun checkBlockForLoopsWithoutYield(
        block: FirBlock,
        ctx: CheckerContext,
        rep: DiagnosticReporter
    ) {
        for (statement in block.statements) {
            when (statement) {
                is FirWhileLoop -> checkLoopBody(statement.block, statement, ctx, rep)
                is FirDoWhileLoop -> checkLoopBody(statement.block, statement, ctx, rep)
                is FirBlock -> checkBlockForLoopsWithoutYield(statement, ctx, rep)
                else -> {}
            }
        }
    }

    private fun checkLoopBody(
        body: FirBlock?,
        loop: org.jetbrains.kotlin.fir.expressions.FirLoop,
        ctx: CheckerContext,
        rep: DiagnosticReporter
    ) {
        if (body == null) return
        if (bodyHasCooperationPoint(body)) return
        rep.reportOn(loop.source, StructuredCoroutinesErrors.LOOP_WITHOUT_YIELD, ctx)
    }

    private fun bodyHasCooperationPoint(block: FirBlock): Boolean {
        for (statement in block.statements) {
            if (statementHasCooperationPoint(statement)) return true
        }
        return false
    }

    private fun statementHasCooperationPoint(statement: FirStatement): Boolean {
        when (statement) {
            is FirFunctionCall -> {
                if (COOPERATION_POINT_NAMES.contains(statement.calleeReference.name)) return true
                if (isSuspendCall(statement)) return true
                for (arg in statement.argumentList.arguments) {
                    if (arg is FirBlock && bodyHasCooperationPoint(arg)) return true
                }
            }
            is FirBlock -> {
                for (s in statement.statements) {
                    if (statementHasCooperationPoint(s)) return true
                }
            }
            else -> {}
        }
        return false
    }

    private fun isSuspendCall(call: FirFunctionCall): Boolean {
        val symbol = call.calleeReference.toResolvedCallableSymbol() as? FirNamedFunctionSymbol ?: return false
        return symbol.resolvedStatus.isSuspend
    }
}
