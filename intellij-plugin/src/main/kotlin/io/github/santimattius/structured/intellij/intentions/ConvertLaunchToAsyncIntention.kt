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
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Intention to convert a launch call to async.
 *
 * Available when the cursor is on a launch call. Converts launch to async
 * to allow returning a Deferred result.
 */
class ConvertLaunchToAsyncIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = StructuredCoroutinesBundle.message("intention.convert.launch.to.async")

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("intention.category")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val callExpression = element.getParentOfType<KtCallExpression>(strict = false) ?: return false
        return callExpression.calleeExpression?.text == "launch"
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val callExpression = element.getParentOfType<KtCallExpression>(strict = false) ?: return

        val factory = KtPsiFactory(project)

        // Get the launch call text and replace "launch" with "async"
        val launchText = callExpression.text
        val asyncText = launchText.replaceFirst("launch", "async")

        val newExpression = factory.createExpression(asyncText)
        callExpression.replace(newExpression)
    }
}
