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
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

/** Replaces default [MutableSharedFlow] with [Channel] for [FLOW_011]. */
class ReplaceSharedFlowWithChannelQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.sharedflow.with.channel")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val property = descriptor.psiElement as? KtProperty ?: return
        val init = property.initializer as? KtCallExpression ?: return
        val typeArgs = init.typeArguments.joinToString(", ") { it.text }
        val channelType = if (typeArgs.isNotBlank()) "<$typeArgs>" else ""
        val factory = KtPsiFactory(project, markGenerated = false)
        val replacement = factory.createExpression("Channel$channelType(Channel.BUFFERED)")
        init.replace(replacement)
    }
}
