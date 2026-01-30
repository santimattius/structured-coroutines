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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Quick fix to replace GlobalScope or inline CoroutineScope with structured alternatives.
 */
sealed class ReplaceGlobalScopeQuickFix(
    private val replacement: String,
    private val nameKey: String
) : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message(nameKey)

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("quickfix.replace.global.scope", replacement)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val dotQualified = element as? KtDotQualifiedExpression ?: return
        val selector = dotQualified.selectorExpression ?: return

        val factory = KtPsiFactory(project)
        val newExpression = factory.createExpression("$replacement.${selector.text}")
        dotQualified.replace(newExpression)
    }

    /**
     * Replace with viewModelScope.
     */
    class WithViewModelScope : ReplaceGlobalScopeQuickFix(
        "viewModelScope",
        "quickfix.replace.global.scope.viewmodel"
    )

    /**
     * Replace with lifecycleScope.
     */
    class WithLifecycleScope : ReplaceGlobalScopeQuickFix(
        "lifecycleScope",
        "quickfix.replace.global.scope.lifecycle"
    )

    /**
     * Replace with coroutineScope { }.
     */
    class WithCoroutineScope : LocalQuickFix {
        override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.replace.global.scope.coroutinescope")

        override fun getFamilyName(): String = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val element = descriptor.psiElement
            val dotQualified = element as? KtDotQualifiedExpression ?: return
            val selector = dotQualified.selectorExpression ?: return

            // Extract the builder call (launch or async) and its lambda
            val selectorText = selector.text
            val factory = KtPsiFactory(project)

            // Transform GlobalScope.launch { ... } to coroutineScope { launch { ... } }
            val newExpression = factory.createExpression("coroutineScope { $selectorText }")
            dotQualified.replace(newExpression)
        }
    }
}
