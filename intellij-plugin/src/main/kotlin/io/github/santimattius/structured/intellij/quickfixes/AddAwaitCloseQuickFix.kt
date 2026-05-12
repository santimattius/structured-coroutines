/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Appends [kotlinx.coroutines.channels.awaitClose] to a [kotlinx.coroutines.flow.callbackFlow] block.
 */
class AddAwaitCloseQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.add.await.close")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? KtCallExpression ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val lambdaArg = call.lambdaArguments.firstOrNull()
            ?: call.valueArguments.filterIsInstance<KtLambdaArgument>().firstOrNull()
            ?: return
        val lambda = lambdaArg.getLambdaExpression() ?: return
        val body = lambda.bodyExpression ?: return

        val awaitCloseLine = "awaitClose { }"
        val inner = when (body) {
            is KtBlockExpression -> body.statements.joinToString("\n") { it.text }
            else -> body.text
        }
        body.replace(factory.createBlock("$inner\n$awaitCloseLine"))
    }
}
