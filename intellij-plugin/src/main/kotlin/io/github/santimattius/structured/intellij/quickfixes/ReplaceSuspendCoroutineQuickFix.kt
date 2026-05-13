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
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Replaces [kotlinx.coroutines.suspendCoroutine] with [kotlinx.coroutines.suspendCancellableCoroutine].
 */
class ReplaceSuspendCoroutineQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.suspend.coroutine.with.cancellable")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? KtCallExpression ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val text = call.text.replaceFirst("suspendCoroutine", "suspendCancellableCoroutine")
        call.replace(factory.createExpression(text))
    }
}
