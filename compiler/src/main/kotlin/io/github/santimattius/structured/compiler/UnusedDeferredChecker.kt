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
import org.jetbrains.kotlin.fir.declarations.FirAnonymousFunction
import org.jetbrains.kotlin.fir.declarations.FirNamedFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirVariable
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.SymbolInternals
import org.jetbrains.kotlin.fir.expressions.FirBlock
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedCallableSymbol
import org.jetbrains.kotlin.fir.visitors.FirVisitorVoid
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
 * 1. Identifies `async` calls assigned to a `val` directly in the function body
 * 2. Collects that variable plus local aliases of it: any `val` whose initializer references
 *    it outside a lambda (`val e = d`, `d as Deferred<*>`, `d!!`, `d ?: other`, `if (c) d else other`)
 * 3. Walks the whole function body for an `await()` / `awaitAll()` call or `onAwait` select clause
 *    whose receiver or arguments reference one of those variables, in any statement position
 *    (`val r = d.await()`, `return d.await()`, `listOf(d).awaitAll()`, inside lambdas, ...)
 * 4. Reports if no such call is found
 *
 * ## Limitations
 *
 * - Only analyzes `val d = async { }` declared directly in the function body
 * - Doesn't follow the Deferred across function boundaries (passed as parameter, stored in a
 *   field or collection, returned)
 * - Doesn't follow `var` reassignment (`e = d`) or a reference only inside a lambda (`val e = run { d }`)
 * - Doesn't recognize an await through a lambda parameter or implicit receiver
 *   (`d.let { it.await() }`, `with(d) { await() }`)
 *
 * @see StructuredCoroutinesErrors.UNUSED_DEFERRED
 */
class UnusedDeferredChecker(
    private val config: PluginConfiguration,
) : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * Name of the async function to detect.
         */
        private val ASYNC_NAME = Name.identifier("async")

        /**
         * Names of the calls that consume a Deferred.
         */
        private val AWAIT_NAMES = setOf(Name.identifier("await"), Name.identifier("awaitAll"))

        /**
         * Name of the `select` clause that consumes a Deferred.
         */
        private val ON_AWAIT_NAME = Name.identifier("onAwait")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        if (expression.calleeReference.name != ASYNC_NAME) return

        val containingBlock = findContainingBlock(expression, context) ?: return
        val deferred = findAssignedVariable(expression, containingBlock) ?: return
        val targets = collectAliases(containingBlock, deferred)

        if (!isAwaited(containingBlock, targets)) {
            reporter.reportUnusedDeferred(expression, context, config)
        }
    }

    /**
     * Returns the symbol of the `val` declared directly in [block] whose initializer is [expression].
     */
    private fun findAssignedVariable(expression: FirFunctionCall, block: FirBlock): FirBasedSymbol<*>? =
        block.statements.filterIsInstance<FirVariable>().firstOrNull { it.initializer == expression }?.symbol

    /**
     * Finds the containing block for the expression.
     *
     * Uses the context to find the function body.
     *
     * @param expression The expression to find the block for
     * @param context The checker context
     * @return The containing block, or null if not found
     */
    @OptIn(SymbolInternals::class)
    private fun findContainingBlock(
        expression: FirExpression,
        context: CheckerContext
    ): FirBlock? {
        // Get the function body from context.
        // In Kotlin 2.3.20+, context.containingDeclarations returns FirBasedSymbol<*> elements;
        // access .fir to get the underlying FirDeclaration for the is-check.
        for (element in context.containingDeclarations) {
            val declaration = element.fir
            if (declaration is FirNamedFunction) {
                val body = declaration.body
                if (body is FirBlock) {
                    return body
                }
            }
        }
        return null
    }

    /**
     * Returns [deferred] plus every local `val` that aliases it, directly or transitively
     * (`val e = d`, `val f = e`). The visitor runs in source order, so chains resolve in one pass.
     *
     * An initializer that references a target anywhere outside a lambda counts, so wrappers such as
     * smart casts, `as`, `!!`, elvis and `if` are covered. This may treat `val e = d ?: other` as
     * an alias when only `other` is awaited, which can only suppress a report, never add one.
     */
    private fun collectAliases(block: FirBlock, deferred: FirBasedSymbol<*>): Set<FirBasedSymbol<*>> {
        val targets = mutableSetOf(deferred)
        block.acceptChildren(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (element is FirProperty && element.initializer?.referencesOutsideLambdas(targets) == true) {
                    targets += element.symbol
                }
                element.acceptChildren(this)
            }
        })
        return targets
    }

    /**
     * Whether any `await()` / `awaitAll()` call or `onAwait` clause in [block] references one of
     * [targets] in its receiver or arguments. Lambdas and local functions are searched too: an await
     * there still consumes the Deferred, and being lenient here can only suppress a report, never add one.
     */
    private fun isAwaited(block: FirBlock, targets: Set<FirBasedSymbol<*>>): Boolean =
        block.containsMatch { element ->
            element.isAwait() && element.containsMatch { it.isReferenceTo(targets) }
        }

    private fun FirElement.isAwait(): Boolean = when (this) {
        is FirFunctionCall -> calleeReference.name in AWAIT_NAMES
        is FirPropertyAccessExpression -> calleeReference.name == ON_AWAIT_NAME
        else -> false
    }

    private fun FirExpression.referencesOutsideLambdas(targets: Set<FirBasedSymbol<*>>): Boolean =
        isReferenceTo(targets) || containsMatch(skip = { it is FirAnonymousFunction }) { it.isReferenceTo(targets) }

    private fun FirElement.isReferenceTo(targets: Set<FirBasedSymbol<*>>): Boolean =
        this is FirPropertyAccessExpression &&
            calleeReference.toResolvedCallableSymbol()?.let { it in targets } == true

    /**
     * Depth-first search over the descendants of this element, short-circuiting on the first match.
     * Elements matching [skip] are not searched, nor are their descendants.
     */
    private fun FirElement.containsMatch(
        skip: (FirElement) -> Boolean = { false },
        predicate: (FirElement) -> Boolean,
    ): Boolean {
        var found = false
        acceptChildren(object : FirVisitorVoid() {
            override fun visitElement(element: FirElement) {
                if (found || skip(element)) return
                if (predicate(element)) {
                    found = true
                    return
                }
                element.acceptChildren(this)
            }
        })
        return found
    }
}
