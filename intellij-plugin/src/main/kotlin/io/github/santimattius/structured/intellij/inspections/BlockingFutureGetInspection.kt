/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceGetWithAwaitQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/** [INTEROP_004] — blocking [java.util.concurrent.Future.get] inside coroutines. */
class BlockingFutureGetInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.blocking.future.get.display.name"
    override val descriptionKey = "inspection.blocking.future.get.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (expression.calleeExpression?.text != "get") return

                val parent = expression.parent as? KtDotQualifiedExpression ?: return
                val receiverText = parent.receiverExpression.text
                if (!receiverText.contains("Future", ignoreCase = true)) return
                if (!isInsideCoroutine(expression)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.interop.blocking.future.get"),
                    ReplaceGetWithAwaitQuickFix(),
                )
            }
        }

    private fun isInsideCoroutine(element: KtCallExpression): Boolean {
        val fn = element.getParentOfType<KtNamedFunction>(strict = true)
        if (fn?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return true
        return CoroutinePsiUtils.isInsideCoroutineContext(element)
    }
}
