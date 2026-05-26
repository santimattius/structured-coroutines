/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.santimattius.structured.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * Unwraps a redundant inner `withContext(sameDispatcher) { body }` to just `body`.
 */
class RemoveRedundantWithContextQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.remove.redundant.withcontext")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val inner = descriptor.psiElement as? KtCallExpression ?: return
        val block = inner.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression as? KtBlockExpression
            ?: return
        val single = block.statements.singleOrNull() ?: return
        inner.replace(single)
    }
}
