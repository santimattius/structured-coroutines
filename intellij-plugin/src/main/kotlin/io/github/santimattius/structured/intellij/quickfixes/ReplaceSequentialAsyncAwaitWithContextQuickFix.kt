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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Replaces `async { }.await()` with `withContext(coroutineContext) { }` for sequential work.
 */
class ReplaceSequentialAsyncAwaitWithContextQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.async.await.with.with.context")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val awaitCall = descriptor.psiElement as? KtCallExpression ?: return
        val dot = awaitCall.parent as? KtDotQualifiedExpression ?: return
        val asyncCall = dot.receiverExpression as? KtCallExpression ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val lambdaArg = asyncCall.lambdaArguments.firstOrNull()
            ?: asyncCall.valueArguments.filterIsInstance<KtLambdaArgument>().firstOrNull()
            ?: return
        val lambdaBody = lambdaArg.getLambdaExpression()?.bodyExpression ?: return
        val inner = when (lambdaBody) {
            is KtBlockExpression -> lambdaBody.statements.joinToString("\n") { it.text }
            else -> lambdaBody.text
        }
        val replacement = factory.createExpression("withContext(coroutineContext) {\n$inner\n}")
        dot.replace(replacement)
    }
}
