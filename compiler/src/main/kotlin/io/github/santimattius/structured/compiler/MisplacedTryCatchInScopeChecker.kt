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
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.expressions.FirAnonymousFunctionExpression
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirTryExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker for EXCEPT_004: a `try/catch` placed INSIDE a `coroutineScope { }` lambda
 * around `await()` / `join()` of a child (`async` / `launch`) created in that same lambda.
 *
 * ## Problem (Best Practice 5.4)
 *
 * The child is a structural child of the `coroutineScope`. When it fails, the failure cancels the
 * scope and `coroutineScope` rethrows to its caller, regardless of the surrounding `try/catch`.
 * The catch may even run, which is misleading, and the code after the `try` never completes
 * normally.
 *
 * ```kotlin
 * // WARNING: the catch cannot stop the failure from leaving coroutineScope
 * suspend fun bad(): Int = coroutineScope {
 *     val d = async<Int> { error("boom") }
 *     try {
 *         d.await()
 *     } catch (e: Exception) {
 *         0
 *     }
 * }
 *
 * // GOOD: wrap the whole coroutineScope call
 * suspend fun good1(): Int =
 *     try { coroutineScope { async<Int> { error("boom") }.await() } } catch (e: Exception) { 0 }
 *
 * // GOOD: catch inside the child
 * suspend fun good2(): Int = coroutineScope {
 *     async { try { error("boom") } catch (e: Exception) { 0 } }.await()
 * }
 *
 * // GOOD: supervisorScope isolates its direct children
 * suspend fun good3(): Int = supervisorScope {
 *     val d = async<Int> { error("boom") }
 *     try { d.await() } catch (e: Exception) { 0 }
 * }
 * ```
 *
 * ## Detection Logic
 *
 * 1. Matches calls named `coroutineScope` resolved to package `kotlinx.coroutines`.
 * 2. Walks the lambda in source order, tracking (by symbol) the local `val`s initialized with an
 *    `async` / `launch` call (kotlinx.coroutines), plus simple aliases (`val e = d`).
 * 3. For every `try` expression that has at least one `catch` clause, reports it when its try
 *    block contains `await()` / `join()` / `awaitAll(...)` on a tracked symbol, or on an inline
 *    `async { }` / `launch { }` call (e.g. `async { }.await()`).
 * 4. Does not descend into lambdas of `async` / `launch` (catching inside the child is the
 *    correct pattern), nor into nested `supervisorScope` (different semantics) or nested
 *    `coroutineScope` lambdas (analyzed by their own call, which avoids double reports).
 *
 * ## Limitations
 *
 * - Only children stored in local `val`s of the same lambda (or awaited inline) are tracked.
 *   Deferreds from parameters, properties, collections or function returns are not (no report).
 * - Aliases are followed only when the initializer is directly a property access to a tracked
 *   child. `var`s that are reassigned are not specially handled.
 * - A `try` that contains the await only inside another builder's lambda (e.g. `withContext`) is
 *   still analyzed, while one inside `async` / `launch` is not.
 * - Receivers wrapped in a smart cast are not unwrapped, so an await on such a receiver is not
 *   matched (false negative).
 * - The arguments of `async` / `launch` are not inspected. A child started with its own parent
 *   job (e.g. `async(SupervisorJob()) { ... }`) is not a structural child of the scope, so its
 *   failure does not cancel the scope and the catch is legitimate, yet it is still reported
 *   (false positive).
 * - Heuristic: a catch that rethrows or intentionally observes the exception is still reported,
 *   hence the WARNING default.
 *
 * @see StructuredCoroutinesErrors.MISPLACED_TRY_CATCH_IN_SCOPE
 */
class MisplacedTryCatchInScopeChecker(
    private val config: PluginConfiguration,
) : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        private const val COROUTINES_PACKAGE = "kotlinx.coroutines"
        private val COROUTINE_SCOPE_NAME = Name.identifier("coroutineScope")
        private val SUPERVISOR_SCOPE_NAME = Name.identifier("supervisorScope")
        private val ASYNC_NAME = Name.identifier("async")
        private val LAUNCH_NAME = Name.identifier("launch")
        private val AWAIT_NAME = Name.identifier("await")
        private val JOIN_NAME = Name.identifier("join")
        private val AWAIT_ALL_NAME = Name.identifier("awaitAll")
        private val JOIN_ALL_NAME = Name.identifier("joinAll")
        private val CHILD_BUILDER_NAMES = setOf(ASYNC_NAME, LAUNCH_NAME)
        private val PRUNED_SCOPE_NAMES = setOf(COROUTINE_SCOPE_NAME, SUPERVISOR_SCOPE_NAME)
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        if (!isCoroutinesCall(expression, COROUTINE_SCOPE_NAME)) return
        val body = lambdaBody(expression) ?: return

        val walker = ScopeWalker()
        body.accept(walker)
        for (tryExpression in walker.misplacedTries) {
            reporter.reportMisplacedTryCatchInScope(tryExpression.source, context, config)
        }
    }

    private fun lambdaBody(call: FirFunctionCall): FirBlock? {
        val lambdaArg = call.argumentList.arguments.lastOrNull() ?: return null
        return (lambdaArg as? FirAnonymousFunctionExpression)?.anonymousFunction?.body
    }

    private fun isCoroutinesCall(call: FirFunctionCall, name: Name): Boolean =
        call.calleeReference.name == name && isFromCoroutines(call)

    private fun isFromCoroutines(call: FirFunctionCall): Boolean {
        val callableId = call.calleeReference.toResolvedCallableSymbol()?.callableId ?: return false
        return callableId.packageName.asString() == COROUTINES_PACKAGE
    }

    private fun isChildBuilder(expression: FirElement?): Boolean =
        expression is FirFunctionCall &&
            expression.calleeReference.name in CHILD_BUILDER_NAMES &&
            isFromCoroutines(expression)

    private fun isPrunedCall(call: FirFunctionCall): Boolean =
        isChildBuilder(call) ||
            (call.calleeReference.name in PRUNED_SCOPE_NAMES && isFromCoroutines(call))

    private fun trackedSymbolOf(expression: FirElement?): FirBasedSymbol<*>? =
        (expression as? FirPropertyAccessExpression)?.calleeReference?.toResolvedCallableSymbol()

    /**
     * Walks the lambda in source order, tracking children created in it and collecting the `try`
     * expressions that wrap an await/join on them.
     */
    private inner class ScopeWalker : FirVisitorVoid() {
        private val children = mutableSetOf<FirBasedSymbol<*>>()
        val misplacedTries = mutableListOf<FirTryExpression>()

        override fun visitElement(element: FirElement) {
            when (element) {
                is FirFunctionCall -> if (isPrunedCall(element)) return
                is FirVariable -> {
                    val init = element.initializer
                    if (isChildBuilder(init) || trackedSymbolOf(init) in children) {
                        children.add(element.symbol)
                    }
                }
                is FirTryExpression -> {
                    if (element.catches.isNotEmpty() && awaitsTrackedChild(element.tryBlock)) {
                        misplacedTries.add(element)
                    }
                }
                else -> {}
            }
            element.acceptChildren(this)
        }

        private fun awaitsTrackedChild(tryBlock: FirBlock): Boolean {
            val finder = AwaitFinder(children)
            tryBlock.accept(finder)
            return finder.found
        }
    }

    /** Looks for await/join/awaitAll/joinAll calls on tracked children, pruning child builders. */
    private inner class AwaitFinder(private val children: Set<FirBasedSymbol<*>>) : FirVisitorVoid() {
        var found: Boolean = false
            private set

        override fun visitElement(element: FirElement) {
            if (found) return
            if (element is FirFunctionCall) {
                if (isPrunedCall(element)) return
                if (isAwaitOrJoinOnChild(element)) {
                    found = true
                    return
                }
            }
            element.acceptChildren(this)
        }

        private fun isAwaitOrJoinOnChild(call: FirFunctionCall): Boolean {
            val name = call.calleeReference.name
            if (!isFromCoroutines(call)) return false
            return when (name) {
                AWAIT_NAME, JOIN_NAME -> isChild(call.explicitReceiver)
                AWAIT_ALL_NAME, JOIN_ALL_NAME ->
                    containsChild(call.explicitReceiver) ||
                        call.argumentList.arguments.any { containsChild(it) }
                else -> false
            }
        }

        private fun isChild(receiver: FirExpression?): Boolean =
            isChildBuilder(receiver) || trackedSymbolOf(receiver) in children

        /** True when [root] (e.g. `listOf(a, b)`) references a tracked child or builds one inline. */
        private fun containsChild(root: FirElement?): Boolean {
            if (root == null) return false
            var hit = false
            root.accept(object : FirVisitorVoid() {
                override fun visitElement(element: FirElement) {
                    if (hit) return
                    if (isChildBuilder(element) || trackedSymbolOf(element) in children) {
                        hit = true
                        return
                    }
                    element.acceptChildren(this)
                }
            })
            return hit
        }
    }
}
