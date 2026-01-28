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
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTryExpression

/**
 * Quick fix to add a CancellationException catch clause before the generic Exception catch.
 */
class AddCancellationExceptionCatchQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.add.cancellation.exception.catch")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val catchClause = descriptor.psiElement.parent as? KtCatchClause
            ?: descriptor.psiElement as? KtCatchClause
            ?: return

        val tryExpression = catchClause.parent as? KtTryExpression ?: return

        val factory = KtPsiFactory(project)

        // Create a new catch clause for CancellationException
        val cancellationCatch = factory.createExpression(
            """
            try {
            } catch (e: CancellationException) {
                throw e
            }
            """.trimIndent()
        ) as KtTryExpression

        val newCatchClause = cancellationCatch.catchClauses.first()

        // Insert the CancellationException catch before the generic catch
        catchClause.parent.addBefore(newCatchClause, catchClause)
    }
}
