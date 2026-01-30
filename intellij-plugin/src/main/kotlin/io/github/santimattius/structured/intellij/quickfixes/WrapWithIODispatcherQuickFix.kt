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
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix to wrap a blocking call with withContext(Dispatchers.IO).
 */
class WrapWithIODispatcherQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.wrap.with.io.dispatcher")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement as? KtCallExpression ?: return

        val factory = KtPsiFactory(project)
        val originalText = element.text
        val newExpression = factory.createExpression(
            "withContext(Dispatchers.IO) { $originalText }"
        )
        element.replace(newExpression)
    }
}
