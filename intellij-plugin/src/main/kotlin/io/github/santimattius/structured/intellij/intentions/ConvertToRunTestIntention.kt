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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Intention to convert runBlocking { delay(...); ... } to runTest { ... } in tests.
 *
 * Available when the cursor is inside a runBlocking call that contains delay().
 * Replaces runBlocking with runTest so the test uses virtual time (TEST_001 / §6.1).
 */
class ConvertToRunTestIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = StructuredCoroutinesBundle.message("intention.convert.to.runtest")

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("intention.category")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val runBlockingCall = findRunBlockingCall(element) ?: return false
        val lambdaArg = runBlockingCall.valueArguments.filterIsInstance<KtLambdaArgument>().firstOrNull()
            ?: runBlockingCall.lambdaArguments.firstOrNull() ?: return false
        val body = lambdaArg.getLambdaExpression()?.bodyExpression ?: return false
        return containsDelay(body)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val runBlockingCall = findRunBlockingCall(element) ?: return
        val factory = KtPsiFactory(project)
        val newText = runBlockingCall.text.replaceFirst("runBlocking", "runTest")
        val newExpression = factory.createExpression(newText)
        runBlockingCall.replace(newExpression)
    }

    private fun findRunBlockingCall(element: PsiElement): KtCallExpression? {
        var current: PsiElement? = element
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "runBlocking") {
                return current
            }
            current = current.parent
        }
        return null
    }

    private fun containsDelay(element: PsiElement): Boolean {
        if (element is KtCallExpression && element.calleeExpression?.text == "delay") return true
        for (child in element.children) {
            if (containsDelay(child)) return true
        }
        return false
    }
}
