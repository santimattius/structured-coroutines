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
 * Quick fix that replaces `withTimeout(...)` with `withTimeoutOrNull(...)`.
 *
 * `withTimeoutOrNull` returns `null` instead of throwing `TimeoutCancellationException`,
 * so the parent scope is not affected on timeout.
 */
class ReplaceWithTimeoutOrNullQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.with.timeout.or.null")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val callExpression = descriptor.psiElement as? KtCallExpression ?: return
        val callee = callExpression.calleeExpression ?: return
        val factory = KtPsiFactory(project)
        callee.replace(factory.createExpression("withTimeoutOrNull"))
    }
}
