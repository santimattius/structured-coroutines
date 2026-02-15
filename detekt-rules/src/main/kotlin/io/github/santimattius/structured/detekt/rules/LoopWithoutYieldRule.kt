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
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
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
class LoopWithoutYieldRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "LoopWithoutYield",
        severity = Severity.Warning,
        description = "[CANCEL_001] Loop in suspend function without cooperation point. " +
            "The coroutine cannot be cancelled until the loop completes. " +
            "Add yield(), ensureActive(), or delay() to allow cancellation. " +
            "See: ${DetektDocUrl.buildDocLink("41-cancel_001--ignoring-cancellation-in-intensive-loops")}",
        debt = Debt.TEN_MINS
    )

    override fun visitForExpression(expression: KtForExpression) {
        super.visitForExpression(expression)
        checkLoop(expression)
    }

    override fun visitWhileExpression(expression: KtWhileExpression) {
        super.visitWhileExpression(expression)
        checkLoop(expression)
    }

    private fun checkLoop(loop: KtLoopExpression) {
        // Check if we're inside a suspend function
        val containingFunction = loop.getParentOfType<KtNamedFunction>(strict = true) ?: return
        if (!CoroutineDetektUtils.isSuspendFunction(containingFunction)) return

        // Check if the loop body contains any cooperation points
        val loopBody = loop.body ?: return
        val callExpressions = loopBody.collectDescendantsOfType<KtCallExpression>()
        
        val hasCooperationPoint = callExpressions.any { call ->
            CoroutineDetektUtils.isCooperationPoint(call) || isSuspendCall(call)
        }

        if (!hasCooperationPoint) {
            val loopType = when (loop) {
                is KtForExpression -> "for"
                is KtWhileExpression -> "while"
                else -> "loop"
            }
            
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(loop),
                    message = "[CANCEL_001] '$loopType' loop in suspend function '${containingFunction.name}' " +
                        "without cooperation point. The coroutine cannot be cancelled during iteration. " +
                        "Add ensureActive() or yield() inside the loop to enable cancellation. " +
                        "See: ${DetektDocUrl.buildDocLink("41-cancel_001--ignoring-cancellation-in-intensive-loops")}"
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
}
