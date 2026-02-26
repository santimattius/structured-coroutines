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
 * Quick fix that inserts a cooperation point at the start of the loop body.
 *
 * In a suspend function there is no implicit CoroutineScope, so we use
 * `currentCoroutineContext().ensureActive()` (CoroutineContext extension), which works in any
 * suspend context. See ensureActive and yield API docs.
 *
 * @see <a href="https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/ensure-active.html">ensureActive</a>
 * @see <a href="https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/yield.html">yield</a>
 */
class AddEnsureActiveInLoopQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.add.ensure.active.in.loop")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        // descriptor.psiElement is the loop when registered from LoopWithoutYieldInspection
        val loop = element as? KtLoopExpression ?: element.getParentOfType<KtLoopExpression>(strict = false) ?: return
        val body = loop.body ?: return
        val factory = KtPsiFactory(project)
        // In suspend functions use currentCoroutineContext().ensureActive() (no implicit CoroutineScope)
        val cooperationPoint = "kotlinx.coroutines.currentCoroutineContext().ensureActive()"
        when (body) {
            is KtBlockExpression -> {
                val statements = body.statements
                val lines = mutableListOf(cooperationPoint)
                statements.forEach { lines.add(it.text) }
                val newBlock = factory.createBlock(lines.joinToString("\n"))
                body.replace(newBlock)
            }
            else -> {
                val newBlock = factory.createBlock(
                    "$cooperationPoint\n${body.text}"
                )
                body.replace(newBlock)
            }
        }
    }
}
