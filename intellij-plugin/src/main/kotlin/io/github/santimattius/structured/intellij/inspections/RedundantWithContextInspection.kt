/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.RemoveRedundantWithContextQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** [CONCUR_004] — nested withContext with the same dispatcher reference. */
class RedundantWithContextInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.redundant.withcontext.display.name"
    override val descriptionKey = "inspection.redundant.withcontext.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (expression.calleeExpression?.text != "withContext") return

                val outerDispatcher = expression.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return
                val inner = findNestedWithContext(expression) ?: return
                val innerDispatcher = inner.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return
                if (outerDispatcher != innerDispatcher) return

                holder.registerProblem(
                    inner,
                    StructuredCoroutinesBundle.message("error.concur.redundant.withcontext"),
                    RemoveRedundantWithContextQuickFix(),
                )
            }
        }

    private fun findNestedWithContext(outer: KtCallExpression): KtCallExpression? {
        val lambda = outer.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return null
        val body = lambda.bodyExpression as? KtBlockExpression ?: return null
        return body.statements.firstOrNull() as? KtCallExpression
    }
}
