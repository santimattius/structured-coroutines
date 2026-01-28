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
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.AddAwaitQuickFix
import io.github.santimattius.structured.intellij.quickfixes.ConvertAsyncToLaunchQuickFix
import io.github.santimattius.structured.intellij.utils.ScopeAnalyzer
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects async calls without corresponding await.
 *
 * async creates a Deferred that should be awaited. If the result is never
 * awaited, it indicates either a bug (result should be used) or that
 * launch should be used instead.
 *
 * Example of problematic code:
 * ```kotlin
 * scope.async {
 *     computeValue()  // Result is never awaited!
 * }
 * ```
 *
 * Recommended fixes:
 * ```kotlin
 * // Option 1: Use the result
 * val result = scope.async { computeValue() }.await()
 *
 * // Option 2: Use launch if result is not needed
 * scope.launch { computeValue() }
 * ```
 */
class AsyncWithoutAwaitInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.async.without.await.display.name"
    override val descriptionKey = "inspection.async.without.await.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val calleeName = expression.calleeExpression?.text ?: return
                if (calleeName != "async") return

                // Check if the Deferred is awaited
                if (!ScopeAnalyzer.isDeferredAwaited(expression)) {
                    val parent = expression.parent as? KtDotQualifiedExpression
                    val elementToHighlight = parent ?: expression

                    holder.registerProblem(
                        elementToHighlight,
                        StructuredCoroutinesBundle.message("error.async.without.await"),
                        AddAwaitQuickFix(),
                        ConvertAsyncToLaunchQuickFix()
                    )
                }
            }
        }
    }
}
