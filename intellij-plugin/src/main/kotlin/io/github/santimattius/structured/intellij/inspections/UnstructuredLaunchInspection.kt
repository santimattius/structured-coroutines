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
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceGlobalScopeQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import io.github.santimattius.structured.intellij.utils.ScopeAnalyzer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects unstructured coroutine launches.
 *
 * Coroutines should be launched within a structured scope that is tied to
 * a lifecycle. Launching coroutines without proper scope management can
 * lead to resource leaks and unpredictable behavior.
 *
 * Example of problematic code:
 * ```kotlin
 * fun doWork() {
 *     someScope.launch { ... }  // Unknown scope lifecycle
 * }
 * ```
 *
 * Recommended alternatives:
 * - Use viewModelScope in ViewModel
 * - Use lifecycleScope in Activity/Fragment
 * - Use @StructuredScope annotated parameters
 * - Use coroutineScope { } or supervisorScope { } builders
 */
class UnstructuredLaunchInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.unstructured.launch.display.name"
    override val descriptionKey = "inspection.unstructured.launch.description"

    private val coroutineLaunchers = setOf("launch", "async")

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val calleeName = expression.calleeExpression?.text ?: return
                if (calleeName !in coroutineLaunchers) return

                // Skip if it's a GlobalScope call (handled by GlobalScopeInspection)
                if (CoroutinePsiUtils.isGlobalScopeCall(expression)) return

                // Skip if it's a framework scope call
                if (CoroutinePsiUtils.isFrameworkScopeCall(expression)) return

                // Skip if it's an inline CoroutineScope (handled by InlineCoroutineScopeInspection)
                if (CoroutinePsiUtils.isInlineCoroutineScopeCreation(expression)) return

                // Skip if we're already inside a structured context (coroutineScope, supervisorScope)
                if (isInsideStructuredBuilder(expression)) return

                // Check if the scope has @StructuredScope annotation
                val parent = expression.parent as? KtDotQualifiedExpression
                if (parent != null) {
                    val scopeName = CoroutinePsiUtils.getScopeName(expression)
                    if (scopeName != null) {
                        val scopeDeclaration = ScopeAnalyzer.findScopeDeclarationByName(expression, scopeName)
                        if (scopeDeclaration != null && ScopeAnalyzer.hasStructuredScopeAnnotation(scopeDeclaration)) {
                            return
                        }
                    }
                }

                // If launch/async is called directly without receiver inside a coroutine builder lambda, it's OK
                if (parent == null && CoroutinePsiUtils.isInsideCoroutineContext(expression)) {
                    return
                }

                // Report unstructured launch only if there's a receiver that's not recognized
                if (parent != null) {
                    holder.registerProblem(
                        expression,
                        StructuredCoroutinesBundle.message("error.unstructured.launch"),
                        ReplaceGlobalScopeQuickFix.WithCoroutineScope()
                    )
                }
            }
        }
    }

    private fun isInsideStructuredBuilder(expression: KtCallExpression): Boolean {
        var current: com.intellij.psi.PsiElement? = expression.parent
        while (current != null) {
            if (current is KtCallExpression) {
                val callee = current.calleeExpression?.text
                if (callee == "coroutineScope" || callee == "supervisorScope") {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

}
