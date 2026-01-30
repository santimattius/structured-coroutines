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
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Intention to extract the body of a coroutine lambda to a suspend function.
 *
 * Available when the cursor is inside a coroutine builder lambda (launch, async).
 * Extracts the lambda body into a new suspend function for better code organization.
 */
class ExtractSuspendFunctionIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = StructuredCoroutinesBundle.message("intention.extract.suspend.function")

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("intention.category")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        // Check if we're inside a lambda of a coroutine builder
        val lambda = element.getParentOfType<KtLambdaExpression>(strict = false) ?: return false
        val parentCall = lambda.parent?.parent as? KtCallExpression ?: return false

        return CoroutinePsiUtils.isCoroutineBuilderCall(parentCall) &&
               parentCall.calleeExpression?.text in setOf("launch", "async")
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val lambda = element.getParentOfType<KtLambdaExpression>(strict = false) ?: return
        val parentCall = lambda.parent?.parent as? KtCallExpression ?: return
        val containingFunction = element.getParentOfType<KtNamedFunction>(strict = false)
        val containingClass = element.getParentOfType<KtClassOrObject>(strict = false)

        val factory = KtPsiFactory(project)

        // Get the lambda body
        val lambdaBody = lambda.bodyExpression ?: return
        val bodyText = lambdaBody.statements.joinToString("\n") { it.text }

        // Generate a function name based on the parent call context
        val baseName = generateFunctionName(parentCall)
        val functionName = findUniqueName(baseName, containingClass, containingFunction)

        // Create the new suspend function
        val newFunction = factory.createFunction(
            """
            private suspend fun $functionName() {
                $bodyText
            }
            """.trimIndent()
        )

        // Replace the lambda body with a call to the new function
        val newLambdaExpression = factory.createExpression("{ $functionName() }") as KtLambdaExpression
        val newLambdaBody = newLambdaExpression.bodyExpression!!
        lambdaBody.replace(newLambdaBody)

        // Add the new function to the containing class or file
        if (containingClass != null) {
            containingClass.body?.addAfter(newFunction, containingClass.body?.lBrace)
        } else {
            // Add to file
            val file = element.containingFile as? KtFile ?: return
            file.add(factory.createWhiteSpace("\n\n"))
            file.add(newFunction)
        }
    }

    private fun generateFunctionName(call: KtCallExpression): String {
        val builderName = call.calleeExpression?.text ?: "coroutine"

        // Try to infer a meaningful name from context
        val parent = call.parent
        if (parent is KtDotQualifiedExpression) {
            val receiver = parent.receiverExpression
            when (receiver) {
                is KtNameReferenceExpression -> {
                    val receiverName = receiver.text
                    return "do${receiverName.replaceFirstChar { it.uppercase() }}Work"
                }
            }
        }

        return "perform${builderName.replaceFirstChar { it.uppercase() }}Work"
    }

    private fun findUniqueName(
        baseName: String,
        containingClass: KtClassOrObject?,
        containingFunction: KtNamedFunction?
    ): String {
        val existingNames = mutableSetOf<String>()

        // Collect existing function names
        containingClass?.declarations?.filterIsInstance<KtNamedFunction>()?.forEach {
            it.name?.let { name -> existingNames.add(name) }
        }

        if (baseName !in existingNames) return baseName

        var counter = 1
        while ("${baseName}$counter" in existingNames) {
            counter++
        }
        return "${baseName}$counter"
    }
}
