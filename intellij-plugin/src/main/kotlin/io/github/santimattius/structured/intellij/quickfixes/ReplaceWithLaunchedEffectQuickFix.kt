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

/** Replaces `scope.launch { }` with `LaunchedEffect(Unit) { }` for [COMPOSE_002]. */
class ReplaceWithLaunchedEffectQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.with.launched.effect")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val launchCall = descriptor.psiElement as? KtCallExpression ?: return
        val parent = launchCall.parent as? KtDotQualifiedExpression ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val lambdaArg = launchCall.lambdaArguments.firstOrNull()?.getLambdaExpression()?.text
            ?: launchCall.valueArguments.firstOrNull()?.text
            ?: "{ }"
        val replacement = factory.createExpression("LaunchedEffect(Unit) $lambdaArg")
        parent.replace(replacement)
    }
}
