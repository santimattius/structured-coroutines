/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Detekt rule that detects loops without cooperation points in suspend functions.
 *
 * ## Problem (Best Practice 4.1)
 *
 * Long-running loops in suspend functions without cooperation points (yield, ensureActive,
 * delay) cannot be cancelled until the loop completes. This defeats the cooperative nature
 * of coroutine cancellation.
 *
 * ```kotlin
 * // ❌ BAD: Loop cannot be cancelled
 * suspend fun processItems(items: List<Item>) {
 *     for (item in items) {
 *         heavyComputation(item)  // No cooperation point
 *     }
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Add cooperation points to allow cancellation:
 *
 * ```kotlin
 * // ✅ GOOD: Loop can be cancelled
 * suspend fun processItems(items: List<Item>) {
 *     for (item in items) {
 *         ensureActive()  // Check for cancellation
 *         heavyComputation(item)
 *     }
 * }
 *
 * // ✅ GOOD: Using yield for CPU-bound work
 * suspend fun processItems(items: List<Item>) {
 *     for (item in items) {
 *         yield()  // Cooperation point
 *         heavyComputation(item)
 *     }
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   LoopWithoutYield:
 *     active: true
 * ```
 *
 * ## Note
 *
 * This is a heuristic rule. It flags loops that don't contain any known cooperation
 * points. False positives may occur if the loop body contains suspend function calls
 * that themselves provide cooperation points.
 *
 * The rule checks for:
 * - yield()
 * - ensureActive()
 * - delay()
 * - suspendCancellableCoroutine
 * - withTimeout / withTimeoutOrNull
 */
class LoopWithoutYieldRule(config: Config = Config.empty) : Rule(
    config,
    description = "[CANCEL_001] Loop in suspend function without cooperation point. " +
            "The coroutine cannot be cancelled until the loop completes. " +
            "Inside a scope builder (launch/async/coroutineScope/supervisorScope/withContext) use ensureActive(); " +
            "otherwise use currentCoroutineContext().ensureActive(), yield(), or delay(). " +
            "See: ${DetektDocUrl.buildDocLink("41-cancel_001--ignoring-cancellation-in-intensive-loops")}",
) {

    override val ruleName: RuleName get() = RuleName("LoopWithoutYield")

    override fun visitForExpression(expression: KtForExpression) {
        super.visitForExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        checkLoop(expression)
    }

    override fun visitWhileExpression(expression: KtWhileExpression) {
        super.visitWhileExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        checkLoop(expression)
    }

    private fun checkLoop(loop: KtLoopExpression) {
        // Check if we're inside a suspend function
        val containingFunction = loop.getParentOfType<KtNamedFunction>(strict = true) ?: return
        if (!CoroutineDetektUtils.isSuspendFunction(containingFunction)) return

        // Check if the loop body contains any cooperation points
        val loopBody = loop.body ?: return
        val hasCooperationPoint = bodyHasCooperationPoint(loopBody)

        if (!hasCooperationPoint) {
            val loopType = when (loop) {
                is KtForExpression -> "for"
                is KtWhileExpression -> "while"
                else -> "loop"
            }
            val insideScope = CoroutineDetektUtils.isInsideScopeBuilderBlock(loop)
            val suggestion = if (insideScope) {
                "Add ensureActive(), yield(), or delay(0) inside the loop to enable cancellation."
            } else {
                "Add currentCoroutineContext().ensureActive(), yield(), or delay(0) inside the loop to enable cancellation."
            }
            report(
                Finding(
                    entity = Entity.from(loop),
                    message = "[CANCEL_001] '$loopType' loop in suspend function '${containingFunction.name}' " +
                        "without cooperation point. The coroutine cannot be cancelled during iteration. " +
                        "$suggestion See: ${DetektDocUrl.buildDocLink("41-cancel_001--ignoring-cancellation-in-intensive-loops")}"
                )
            )
        }
    }

    /**
     * Heuristic check if a call might be a suspend function call.
     * This is a simple heuristic based on common naming patterns.
     */
    private fun isSuspendCall(call: KtCallExpression): Boolean {
        val calleeName = call.calleeExpression?.text ?: return false

        // Common suspend function naming patterns
        return calleeName.startsWith("await") ||
            calleeName.startsWith("suspend") ||
            calleeName.startsWith("fetch") ||
            calleeName.startsWith("load") ||
            calleeName.startsWith("get") && calleeName.endsWith("Async") ||
            calleeName.endsWith("Suspending") ||
            calleeName == "emit" ||
            calleeName == "collect" ||
            calleeName == "send" ||
            calleeName == "receive"
    }

    /**
     * Deep, structural search for a cooperation point anywhere in [body]'s statement tree —
     * mirrors the compiler's [io.github.santimattius.structured.compiler.LoopWithoutYieldChecker]
     * `CooperationPointFinder` (#66) so the two surfaces stay in parity.
     */
    private fun bodyHasCooperationPoint(body: KtExpression): Boolean {
        val finder = CooperationPointFinder()
        body.accept(finder)
        return finder.found
    }

    /**
     * Depth-first walk over a loop body that short-circuits as soon as a cooperation point is
     * found anywhere in the statement tree.
     *
     * Pruning policy: named function/property-accessor *declarations* are not descended into — a
     * local `suspend fun helper() { delay(1) }` that is declared but never called inside the loop
     * must not suppress the diagnostic (see #66). Anonymous functions (`fun() { }`, a nameless
     * [KtNamedFunction]) and lambda bodies are NOT pruned and are still searched, since they
     * execute inline at their call site (e.g. a lambda passed to `run { }`).
     */
    private inner class CooperationPointFinder : KtTreeVisitorVoid() {
        var found: Boolean = false
            private set

        override fun visitNamedFunction(function: KtNamedFunction) {
            if (function.name != null) return // prune declaration, keep anonymous fun
            super.visitNamedFunction(function)
        }

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) = Unit // prune

        override fun visitCallExpression(expression: KtCallExpression) {
            if (found) return
            if (CoroutineDetektUtils.isCooperationPoint(expression) || isSuspendCall(expression)) {
                found = true
                return
            }
            super.visitCallExpression(expression)
        }
    }
}
