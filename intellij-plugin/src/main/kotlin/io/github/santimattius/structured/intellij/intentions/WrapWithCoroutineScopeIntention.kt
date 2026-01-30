/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Intention to wrap the content of a suspend function with coroutineScope { }.
 *
 * Available when inside a suspend function that doesn't already have a
 * coroutineScope wrapper. Useful for creating a structured scope within
 * the suspend function.
 */
class WrapWithCoroutineScopeIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = StructuredCoroutinesBundle.message("intention.wrap.with.coroutine.scope")

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("intention.category")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // Check if we're in a suspend function
        val function = element.getParentOfType<KtNamedFunction>(strict = false) ?: return false
        if (!function.hasModifier(KtTokens.SUSPEND_KEYWORD)) return false

        // Check if the function body already uses coroutineScope
        val bodyExpression = function.bodyExpression ?: return false

        // Don't offer if already wrapped in coroutineScope
        if (bodyExpression is KtCallExpression && bodyExpression.calleeExpression?.text == "coroutineScope") {
            return false
        }

        // Check if there's a coroutineScope at the top level
        if (bodyExpression is KtBlockExpression) {
            val firstStatement = bodyExpression.statements.firstOrNull()
            if (firstStatement is KtCallExpression && firstStatement.calleeExpression?.text == "coroutineScope") {
                return false
            }
        }

        return true
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val function = element.getParentOfType<KtNamedFunction>(strict = false) ?: return
        val bodyExpression = function.bodyExpression ?: return

        val factory = KtPsiFactory(project)

        val bodyText = when (bodyExpression) {
            is KtBlockExpression -> {
                val statements = bodyExpression.statements.joinToString("\n") { it.text }
                statements
            }
            else -> bodyExpression.text
        }

        // Create new body with coroutineScope
        val newBody = factory.createExpression("coroutineScope {\n$bodyText\n}")

        // Replace the body
        if (function.hasBlockBody()) {
            val newBlock = factory.createBlock("coroutineScope {\n$bodyText\n}")
            bodyExpression.replace(newBlock)
        } else {
            // Expression body - wrap in coroutineScope
            function.bodyExpression?.replace(newBody)
        }
    }
}
