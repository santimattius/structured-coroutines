/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix to remove runBlocking and unwrap its content.
 */
class RemoveRunBlockingQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.remove.run.blocking")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? KtCallExpression ?: return

        // Get the lambda content
        val lambdaArg = element.valueArguments
            .filterIsInstance<KtLambdaArgument>()
            .firstOrNull()
            ?: element.lambdaArguments.firstOrNull()
            ?: return

        val lambdaBody = lambdaArg.getLambdaExpression()?.bodyExpression ?: return
        val bodyText = lambdaBody.statements.joinToString("\n") { it.text }

        if (bodyText.isNotBlank()) {
            val factory = KtPsiFactory(project)

            // If there's a single expression, replace directly
            // If multiple statements, we need to handle differently based on context
            if (lambdaBody.statements.size == 1) {
                val newExpression = factory.createExpression(bodyText)
                element.replace(newExpression)
            } else {
                // For multiple statements, wrap in a block or keep as statements
                val newExpression = factory.createBlock(bodyText)
                element.replace(newExpression)
            }
        } else {
            // Empty body, just delete
            element.delete()
        }
    }
}
