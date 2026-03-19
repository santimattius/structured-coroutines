/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceWithTimeoutOrNullQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Inspection that detects `withTimeout` calls not wrapped in a try/catch that
 * handles `TimeoutCancellationException` (or a parent exception type).
 *
 * ## Problem (§4.6 CANCEL_006)
 *
 * `withTimeout` throws `TimeoutCancellationException` on timeout, which propagates
 * upward and cancels the **parent scope** if uncaught. This can silently kill
 * sibling coroutines that were not involved in the timeout.
 *
 * Prefer `withTimeoutOrNull` to receive a `null` result on timeout without scope
 * cancellation. If you need the exception, catch `TimeoutCancellationException`
 * explicitly.
 *
 * Example of problematic code:
 * ```kotlin
 * scope.launch {
 *     withTimeout(5_000) { fetchData() }  // cancels the whole scope on timeout!
 * }
 * ```
 *
 * Recommended fixes:
 * ```kotlin
 * // Option 1: use withTimeoutOrNull
 * val result = withTimeoutOrNull(5_000) { fetchData() }
 *
 * // Option 2: catch the exception explicitly
 * try {
 *     withTimeout(5_000) { fetchData() }
 * } catch (e: TimeoutCancellationException) {
 *     // handle timeout without cancelling the scope
 * }
 * ```
 */
class WithTimeoutScopeCancellationInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.with.timeout.scope.cancellation.display.name"
    override val descriptionKey = "inspection.with.timeout.scope.cancellation.description"

    /**
     * Exception type names whose presence in a catch clause means the
     * `TimeoutCancellationException` is properly handled.
     */
    private val handledExceptions = setOf(
        "TimeoutCancellationException",
        "CancellationException",
        "Exception",
        "Throwable"
    )

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                if (!CoroutinesImportFilter.callIsInCoroutinesFile(expression)) return

                val callee = expression.calleeExpression?.text ?: return
                if (callee != "withTimeout") return

                // Only flag inside a coroutine/suspend context
                if (!CoroutinePsiUtils.isInsideCoroutineContext(expression) &&
                    !CoroutinePsiUtils.isInSuspendFunction(expression)
                ) return

                // If wrapped in a try that catches the timeout, skip
                if (isInsideHandlingTryCatch(expression)) return

                holder.registerProblem(
                    expression.calleeExpression as PsiElement,
                    StructuredCoroutinesBundle.message("error.with.timeout.scope.cancellation"),
                    ReplaceWithTimeoutOrNullQuickFix()
                )
            }
        }
    }

    private fun isInsideHandlingTryCatch(expression: KtCallExpression): Boolean {
        val tryExpression = expression.getParentOfType<KtTryExpression>(strict = true)
            ?: return false
        return tryExpression.catchClauses.any { clause ->
            val typeName = clause.catchParameter?.typeReference?.text
            typeName != null && typeName in handledExceptions
        }
    }
}
