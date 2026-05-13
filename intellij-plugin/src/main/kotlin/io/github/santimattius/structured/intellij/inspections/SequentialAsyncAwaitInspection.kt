/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceSequentialAsyncAwaitWithContextQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * [CONCUR_003] — Sequential `async { }.await()` adds [kotlinx.coroutines.Deferred] overhead without parallelism.
 */
class SequentialAsyncAwaitInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.sequential.async.await.display.name"
    override val descriptionKey = "inspection.sequential.async.await.description"

    override fun getShortName(): String = "SequentialAsyncAwait"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)
                val file = expression.containingKtFile
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return

                val selector = expression.selectorExpression as? KtCallExpression ?: return
                if (selector.calleeExpression?.text != "await") return

                val asyncCall = expression.receiverExpression as? KtCallExpression ?: return
                if (asyncCall.calleeExpression?.text != "async") return

                holder.registerProblem(
                    selector,
                    StructuredCoroutinesBundle.message("error.concur.sequential.async.await"),
                    ReplaceSequentialAsyncAwaitWithContextQuickFix()
                )
            }
        }
}
