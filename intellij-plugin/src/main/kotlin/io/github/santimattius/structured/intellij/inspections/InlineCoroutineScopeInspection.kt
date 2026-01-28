/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceGlobalScopeQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects inline CoroutineScope creation.
 *
 * Creating inline CoroutineScope instances bypasses structured concurrency
 * because the scope is not tied to any lifecycle and its cancellation
 * must be managed manually.
 *
 * Example of problematic code:
 * ```kotlin
 * CoroutineScope(Dispatchers.IO).launch {
 *     fetchData()  // Scope is not managed, will run until completion
 * }
 * ```
 *
 * Recommended alternatives:
 * - Use viewModelScope in ViewModel
 * - Use lifecycleScope in Activity/Fragment
 * - Create a managed scope as a class property with proper cleanup
 * - Use coroutineScope { } builder for scoped work
 */
class InlineCoroutineScopeInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.inline.scope.display.name"
    override val descriptionKey = "inspection.inline.scope.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                if (CoroutinePsiUtils.isInlineCoroutineScopeCreation(expression)) {
                    val builderName = expression.calleeExpression?.text ?: "launch"
                    val parent = expression.parent as? KtDotQualifiedExpression
                    val elementToHighlight: PsiElement = parent ?: expression

                    holder.registerProblem(
                        elementToHighlight,
                        StructuredCoroutinesBundle.message("error.inline.scope", builderName),
                        ReplaceGlobalScopeQuickFix.WithViewModelScope(),
                        ReplaceGlobalScopeQuickFix.WithLifecycleScope(),
                        ReplaceGlobalScopeQuickFix.WithCoroutineScope()
                    )
                }
            }
        }
    }
}
