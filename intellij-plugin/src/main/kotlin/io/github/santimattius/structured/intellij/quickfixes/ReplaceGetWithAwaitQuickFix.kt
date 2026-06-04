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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/** Replaces `.get()` with `.await()` for [INTEROP_004]. */
class ReplaceGetWithAwaitQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.get.with.await")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val getCall = descriptor.psiElement as? KtCallExpression ?: return
        val dotExpr = getCall.parent as? KtDotQualifiedExpression ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val receiver = dotExpr.receiverExpression.text
        val replacement = factory.createExpression("$receiver.await()")
        dotExpr.replace(replacement)
    }
}
