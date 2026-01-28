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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix to replace scope.cancel() with scope.coroutineContext.job.cancelChildren().
 */
class ReplaceCancelWithCancelChildrenQuickFix(
    private val scopeName: String
) : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.replace.cancel.with.cancel.children")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val dotQualified = element.parent as? KtDotQualifiedExpression ?: return

        val factory = KtPsiFactory(project)
        val newExpression = factory.createExpression(
            "$scopeName.coroutineContext.job.cancelChildren()"
        )
        dotQualified.replace(newExpression)
    }
}
