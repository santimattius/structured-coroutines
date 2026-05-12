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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Inserts a [kotlinx.coroutines.flow.catch] before the terminal Flow operator.
 */
class AddCatchOperatorQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.add.catch.operator")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val terminal = descriptor.psiElement as? KtCallExpression ?: return
        val dot = terminal.parent as? KtDotQualifiedExpression ?: return
        val receiver = dot.receiverExpression
        val selector = dot.selectorExpression ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val replacement = factory.createExpression(
            "${receiver.text}.catch { e -> throw e }.${selector.text}"
        )
        dot.replace(replacement)
    }
}
