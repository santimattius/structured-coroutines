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
import org.jetbrains.kotlin.psi.KtCatchClause
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Quick fix to add a CancellationException catch clause before the generic Exception catch.
 */
class AddCancellationExceptionCatchQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.add.cancellation.exception.catch")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val catchClause = element as? KtCatchClause ?: element.getParentOfType<KtCatchClause>(strict = false) ?: return
        val tryExpression = catchClause.getParentOfType<KtTryExpression>(strict = false) ?: return

        val factory = KtPsiFactory(project)
        val tempTry = factory.createExpression(
            "try { } catch (e: kotlinx.coroutines.CancellationException) { throw e }"
        ) as KtTryExpression
        val newCatchClause = tempTry.catchClauses.first()

        tryExpression.addBefore(newCatchClause, catchClause)
    }
}
