/**
 * Copyright 2024 Santiago Mattiauda
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
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Intention to migrate a coroutine scope to viewModelScope.
 *
 * Available when inside a ViewModel class and the cursor is on a coroutine
 * builder (launch, async) that's not already using viewModelScope.
 */
class MigrateToViewModelScopeIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = StructuredCoroutinesBundle.message("intention.migrate.to.viewmodel.scope")

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("intention.category")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // Check if we're on a coroutine builder call
        val callExpression = element.getParentOfType<KtCallExpression>(strict = false) ?: return false
        val calleeName = callExpression.calleeExpression?.text ?: return false
        if (calleeName !in setOf("launch", "async")) return false

        // Check if already using viewModelScope
        if (CoroutinePsiUtils.isFrameworkScopeCall(callExpression)) return false

        // Check if inside a ViewModel class
        val classDeclaration = element.getParentOfType<KtClass>(strict = false) ?: return false
        return CoroutinePsiUtils.isViewModelClass(classDeclaration)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val callExpression = element.getParentOfType<KtCallExpression>(strict = false) ?: return
        val parent = callExpression.parent

        val factory = KtPsiFactory(project)

        when (parent) {
            is KtDotQualifiedExpression -> {
                // Replace receiver.launch { } with viewModelScope.launch { }
                val selector = parent.selectorExpression?.text ?: return
                val newExpression = factory.createExpression("viewModelScope.$selector")
                parent.replace(newExpression)
            }
            else -> {
                // Add viewModelScope. prefix
                val callText = callExpression.text
                val newExpression = factory.createExpression("viewModelScope.$callText")
                callExpression.replace(newExpression)
            }
        }
    }
}
