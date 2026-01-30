/**
 * Copyright 2026 Santiago Mattiauda
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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix to replace scope.launch(Job()) or scope.launch(SupervisorJob())
 * with supervisorScope { launch { ... } }.
 */
class ReplaceJobWithSupervisorScopeQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.replace.job.with.supervisor.scope")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? KtCallExpression ?: return

        val factory = KtPsiFactory(project)

        // Get the lambda body
        val lambdaArg = element.valueArguments
            .filterIsInstance<KtLambdaArgument>()
            .firstOrNull()
            ?: element.lambdaArguments.firstOrNull()

        val lambdaText = lambdaArg?.getLambdaExpression()?.bodyExpression?.text?.trim() ?: ""

        // Get the builder name (launch or async)
        val builderName = element.calleeExpression?.text ?: "launch"

        // Get non-Job arguments (like dispatcher)
        val otherArgs = element.valueArguments
            .filter { it !is KtLambdaArgument }
            .filter { !it.text.contains("Job()") && !it.text.contains("SupervisorJob()") }
            .joinToString(", ") { it.text }

        val argsString = if (otherArgs.isNotEmpty()) "($otherArgs)" else ""

        // Create supervisorScope { launch { ... } }
        val newExpression = factory.createExpression(
            "supervisorScope { $builderName$argsString { $lambdaText } }"
        )

        // If the original was scope.launch, we need to handle the parent
        val parent = element.parent
        if (parent is KtDotQualifiedExpression) {
            parent.replace(newExpression)
        } else {
            element.replace(newExpression)
        }
    }
}
