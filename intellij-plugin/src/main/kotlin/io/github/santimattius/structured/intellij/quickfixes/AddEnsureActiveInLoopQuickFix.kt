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
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Quick fix that inserts ensureActive() at the start of the loop body.
 */
class AddEnsureActiveInLoopQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.add.ensure.active.in.loop")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val loop = element.getParentOfType<KtLoopExpression>(strict = true) ?: return
        val body = loop.body ?: return
        val factory = KtPsiFactory(project)
        val ensureActiveStatement = factory.createExpression("kotlinx.coroutines.ensureActive()")
        when (body) {
            is KtBlockExpression -> {
                body.addBefore(ensureActiveStatement, body.statements.firstOrNull())
            }
            else -> {
                val newBlock = factory.createBlock("kotlinx.coroutines.ensureActive()\n${body.text}")
                body.replace(newBlock)
            }
        }
    }
}
