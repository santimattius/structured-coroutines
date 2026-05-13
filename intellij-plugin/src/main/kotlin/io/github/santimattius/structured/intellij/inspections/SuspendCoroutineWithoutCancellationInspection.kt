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
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceSuspendCoroutineQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * [INTEROP_001] — `suspendCoroutine` does not support coroutine cancellation.
 *
 * Detects calls to `suspendCoroutine { }` inside suspend functions and suggests
 * replacing them with `suspendCancellableCoroutine` to properly support cancellation.
 *
 * Exclusion: jvmMain code with explicit try/finally cleanup block is excluded to avoid
 * false positives when the developer has already handled resource cleanup.
 */
class SuspendCoroutineWithoutCancellationInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.suspend.coroutine.without.cancellation.display.name"
    override val descriptionKey = "inspection.suspend.coroutine.without.cancellation.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (expression.calleeExpression?.text != "suspendCoroutine") return

                val file = expression.containingKtFile
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return

                // Must be inside a suspend function
                val enclosingFun = expression.getParentOfType<KtNamedFunction>(strict = true)
                    ?: return
                if (!enclosingFun.hasModifier(KtTokens.SUSPEND_KEYWORD)) return

                // Exclusion: if inside try/finally with explicit cleanup, skip
                if (isInsideTryFinallyCleanup(expression)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.interop.suspend.coroutine.without.cancellation"),
                    ReplaceSuspendCoroutineQuickFix()
                )
            }
        }

    /**
     * Returns true if the suspendCoroutine call is inside a try block that has a
     * finally section — indicating the developer is already managing cleanup.
     */
    private fun isInsideTryFinallyCleanup(expression: KtCallExpression): Boolean {
        val tryExpr = expression.getParentOfType<KtTryExpression>(strict = true) ?: return false
        return tryExpr.finallyBlock != null
    }
}
