/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceSynchronizedWithMutexQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/** [CONCUR_001] — synchronized inside coroutines. */
class SynchronizedInCoroutineInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.synchronized.in.coroutine.display.name"
    override val descriptionKey = "inspection.synchronized.in.coroutine.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (expression.calleeExpression?.text != "synchronized") return
                if (!isInsideCoroutine(expression)) return
                if (isInsideMutexWithLock(expression)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.concur.synchronized.in.coroutine"),
                    ReplaceSynchronizedWithMutexQuickFix(),
                )
            }
        }

    private fun isInsideCoroutine(element: KtCallExpression): Boolean {
        val fn = element.getParentOfType<KtNamedFunction>(strict = true)
        if (fn?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return true
        var current = element.parent
        while (current != null) {
            val call = current as? KtCallExpression
            if (call?.calleeExpression?.text in setOf("launch", "async", "withContext", "coroutineScope", "supervisorScope")) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun isInsideMutexWithLock(element: KtCallExpression): Boolean {
        var current = element.parent
        while (current != null) {
            if ((current as? KtCallExpression)?.calleeExpression?.text == "withLock") return true
            current = current.parent
        }
        return false
    }
}
