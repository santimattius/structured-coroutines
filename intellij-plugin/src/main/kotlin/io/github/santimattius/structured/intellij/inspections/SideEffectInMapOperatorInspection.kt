/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** [FLOW_008] — side effects inside Flow.map. */
class SideEffectInMapOperatorInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.sideeffect.in.map.display.name"
    override val descriptionKey = "inspection.sideeffect.in.map.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (expression.calleeExpression?.text != "map") return
                val lambda = expression.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
                if (!hasSideEffectBeforeReturn(lambda)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.flow.sideeffect.in.map"),
                )
            }
        }

    private fun hasSideEffectBeforeReturn(lambda: KtLambdaExpression): Boolean {
        val body = lambda.bodyExpression ?: return false
        val statements = body.statements
        if (statements.size < 2) return false
        val hints = setOf("track", "log", "println", "print", "debug", "info", "warn", "error", "insert", "update", "delete", "execute", "emit")
        return statements.dropLast(1).any { stmt ->
            var found = false
            stmt.accept(
                object : KtVisitorVoid() {
                    override fun visitCallExpression(expression: KtCallExpression) {
                        val callee = expression.calleeExpression?.text ?: ""
                        val selector = (expression.parent as? KtDotQualifiedExpression)?.selectorExpression?.text
                        if (callee in hints || selector in hints) found = true
                        super.visitCallExpression(expression)
                    }
                },
            )
            found
        }
    }
}
