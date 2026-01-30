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
import org.jetbrains.kotlin.psi.KtFinallySection
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Quick fix to wrap suspend calls in finally block with withContext(NonCancellable).
 */
class WrapWithNonCancellableQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.wrap.with.non.cancellable")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement

        // Try to wrap the entire finally block content
        val finallySection = element.getParentOfType<KtFinallySection>(strict = false)
        val blockExpression = finallySection?.finalExpression

        val factory = KtPsiFactory(project)

        if (blockExpression != null) {
            val bodyText = blockExpression.text.trim()
            // Remove outer braces if present
            val innerText = if (bodyText.startsWith("{") && bodyText.endsWith("}")) {
                bodyText.substring(1, bodyText.length - 1).trim()
            } else {
                bodyText
            }

            val newExpression = factory.createBlock(
                "withContext(NonCancellable) {\n$innerText\n}"
            )
            blockExpression.replace(newExpression)
        } else {
            // Just wrap the single call
            val originalText = element.text
            val newExpression = factory.createExpression(
                "withContext(NonCancellable) { $originalText }"
            )
            element.replace(newExpression)
        }
    }
}
