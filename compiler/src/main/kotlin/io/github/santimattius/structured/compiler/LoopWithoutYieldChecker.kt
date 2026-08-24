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
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirPropertyAccessor
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirDoWhileLoop
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirWhileLoop
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.expressions.FirLoop
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
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
    override fun check(declaration: FirNamedFunction) {
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

    /**
     * Deep, structural search for a cooperation point anywhere in [block]'s statement tree —
     * property initializers, assignments, conditions, `when` branches, elvis RHS, `try`/`catch`
     * bodies, and so on. A generic FIR walk is used instead of enumerating node kinds so the
     * next unlisted node shape cannot silently reintroduce a false negative (see #66).
     */
    private fun bodyHasCooperationPoint(block: FirBlock): Boolean {
        val finder = CooperationPointFinder()
        block.accept(finder)
        return finder.found
    }

    private fun isCooperationPoint(call: FirFunctionCall): Boolean {
        if (COOPERATION_POINT_NAMES.contains(call.calleeReference.name)) return true
        return isSuspendCall(call)
    }

    private fun isSuspendCall(call: FirFunctionCall): Boolean {
        val symbol = call.calleeReference.toResolvedCallableSymbol() as? FirNamedFunctionSymbol ?: return false
        return symbol.resolvedStatus.isSuspend
    }

    /**
     * Depth-first walk over a loop body that short-circuits as soon as a cooperation point is
     * found anywhere in the statement tree.
     *
     * Pruning policy: named function/accessor *declarations* are not descended into — a local
     * `suspend fun helper() { delay(1) }` that is declared but never called inside the loop must
     * not suppress the diagnostic. Lambda bodies ([org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction])
     * are NOT pruned and are still searched, since a lambda passed to e.g. `run { }` executes
     * inline at the call site.
     */
    private inner class CooperationPointFinder : FirVisitorVoid() {
        var found: Boolean = false
            private set

        override fun visitElement(element: FirElement) {
            if (found) return
            when (element) {
                is FirNamedFunction, is FirPropertyAccessor -> return
                is FirFunctionCall -> {
                    if (isCooperationPoint(element)) {
                        found = true
                        return
                    }
                }
                else -> {}
            }
            element.acceptChildren(this)
        }
    }
}
