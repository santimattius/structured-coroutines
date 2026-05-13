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
import io.github.santimattius.structured.intellij.quickfixes.AddAwaitCloseQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * [INTEROP_002] — `callbackFlow` without `awaitClose` causes `IllegalStateException` at runtime
 * and leaks registered listeners.
 *
 * Detects `callbackFlow { }` lambda bodies that do not contain any `awaitClose(...)` call.
 *
 * Exclusion: `channelFlow` has different semantics and does not require `awaitClose`.
 */
class CallbackFlowWithoutAwaitCloseInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.callback.flow.without.await.close.display.name"
    override val descriptionKey = "inspection.callback.flow.without.await.close.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (expression.calleeExpression?.text != "callbackFlow") return

                val file = expression.containingKtFile
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return

                // Get the trailing lambda body of callbackFlow
                val lambdaArg = expression.lambdaArguments.firstOrNull()
                    ?: expression.valueArguments.filterIsInstance<KtLambdaArgument>().firstOrNull()
                    ?: return

                val lambdaBody = lambdaArg.getLambdaExpression()?.bodyExpression ?: return

                // Check if awaitClose is present anywhere in the lambda body
                val hasAwaitClose = lambdaBody.collectDescendantsOfType<KtCallExpression>()
                    .any { it.calleeExpression?.text == "awaitClose" }

                if (!hasAwaitClose) {
                    holder.registerProblem(
                        expression,
                        StructuredCoroutinesBundle.message("error.interop.callback.flow.without.await.close"),
                        AddAwaitCloseQuickFix()
                    )
                }
            }
        }
}
