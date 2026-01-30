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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix to convert async to launch when the result is not used.
 */
class ConvertAsyncToLaunchQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.convert.async.to.launch")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement

        val asyncCall = when (element) {
            is KtCallExpression -> element
            is KtDotQualifiedExpression -> element.selectorExpression as? KtCallExpression
            else -> null
        } ?: return

        val factory = KtPsiFactory(project)

        // Get the async call text and replace "async" with "launch"
        val asyncText = asyncCall.text
        val launchText = asyncText.replaceFirst("async", "launch")

        when (element) {
            is KtDotQualifiedExpression -> {
                val receiver = element.receiverExpression.text
                val newExpression = factory.createExpression("$receiver.$launchText")
                element.replace(newExpression)
            }
            is KtCallExpression -> {
                val newExpression = factory.createExpression(launchText)
                element.replace(newExpression)
            }
        }
    }
}
