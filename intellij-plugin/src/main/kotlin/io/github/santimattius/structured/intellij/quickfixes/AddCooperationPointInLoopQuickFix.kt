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
 * Quick fix that inserts a cooperation point (snippet) at the start of the loop body.
 * Used for CANCEL_001: ensureActive() (in scope), currentCoroutineContext().ensureActive() (suspend),
 * yield(), or delay(0).
 */
class AddCooperationPointInLoopQuickFix(
    private val snippet: String,
    private val messageKey: String
) : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message(messageKey)

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("quickfix.add.cooperation.point.in.loop.family")

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val loop = element as? KtLoopExpression ?: element.getParentOfType<KtLoopExpression>(strict = false) ?: return
        val body = loop.body ?: return
        val factory = KtPsiFactory(project)
        when (body) {
            is KtBlockExpression -> {
                val statements = body.statements
                val lines = mutableListOf(snippet)
                statements.forEach { lines.add(it.text) }
                val newBlock = factory.createBlock(lines.joinToString("\n"))
                body.replace(newBlock)
            }
            else -> {
                val newBlock = factory.createBlock("$snippet\n${body.text}")
                body.replace(newBlock)
            }
        }
    }
}
