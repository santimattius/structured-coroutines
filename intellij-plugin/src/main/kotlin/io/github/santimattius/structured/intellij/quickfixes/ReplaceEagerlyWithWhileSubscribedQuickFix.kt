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
import org.jetbrains.kotlin.psi.KtPsiFactory

class ReplaceEagerlyWithWhileSubscribedQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.eagerly.with.while.subscribed")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val call = descriptor.psiElement as? KtCallExpression ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val text = call.text
            .replace("SharingStarted.Eagerly", "SharingStarted.WhileSubscribed(5_000)")
            .replace("Eagerly", "WhileSubscribed(5_000)")
        call.replace(factory.createExpression(text))
    }
}
