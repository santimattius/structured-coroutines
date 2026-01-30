/**
 * Copyright 2026 Santiago Mattiauda
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
 * Inspection that detects GlobalScope usage.
 *
 * GlobalScope bypasses structured concurrency and can lead to resource leaks.
 * Coroutines launched on GlobalScope are not tied to any lifecycle and will run
 * until completion regardless of cancellation.
 *
 * Example of problematic code:
 * ```kotlin
 * GlobalScope.launch {
 *     fetchData()  // This coroutine will run even if the component is destroyed
 * }
 * ```
 *
 * Recommended alternatives:
 * - viewModelScope (in ViewModel)
 * - lifecycleScope (in Activity/Fragment)
 * - coroutineScope { } (structured builder)
 * - @StructuredScope annotated scopes
 */
class GlobalScopeInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.global.scope.display.name"
    override val descriptionKey = "inspection.global.scope.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                if (CoroutinePsiUtils.isGlobalScopeCall(expression)) {
                    val builderName = expression.calleeExpression?.text ?: "launch"
                    val parent = expression.parent as? KtDotQualifiedExpression
                    val elementToHighlight: PsiElement = parent ?: expression

                    holder.registerProblem(
                        elementToHighlight,
                        StructuredCoroutinesBundle.message("error.global.scope.usage", builderName),
                        ReplaceGlobalScopeQuickFix.WithViewModelScope(),
                        ReplaceGlobalScopeQuickFix.WithLifecycleScope(),
                        ReplaceGlobalScopeQuickFix.WithCoroutineScope()
                    )
                }
            }
        }
    }
}
