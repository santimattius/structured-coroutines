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
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects usage of Dispatchers.Unconfined.
 *
 * Dispatchers.Unconfined runs coroutines in the current thread until the first
 * suspension point, then resumes in whatever thread the suspending function
 * used. This leads to unpredictable execution and should be avoided except
 * in specific testing scenarios.
 *
 * Example of problematic code:
 * ```kotlin
 * withContext(Dispatchers.Unconfined) {
 *     // Unpredictable which thread this runs on after suspension
 *     fetchData()
 * }
 * ```
 *
 * Recommended alternatives:
 * - Use Dispatchers.Default for CPU-intensive work
 * - Use Dispatchers.IO for I/O operations
 * - Use Dispatchers.Main for UI updates
 * - Use runTest { } with Dispatchers.Unconfined for tests only
 */
class DispatchersUnconfinedInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.dispatchers.unconfined.display.name"
    override val descriptionKey = "inspection.dispatchers.unconfined.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)

                // Check for Dispatchers.Unconfined
                val receiver = expression.receiverExpression
                val selector = expression.selectorExpression

                if (receiver is KtNameReferenceExpression &&
                    receiver.text == "Dispatchers" &&
                    selector is KtNameReferenceExpression &&
                    selector.text == "Unconfined") {

                    // Allow in test files
                    val fileName = expression.containingFile.name
                    if (isTestFile(fileName)) return

                    holder.registerProblem(
                        expression,
                        StructuredCoroutinesBundle.message("error.dispatchers.unconfined")
                    )
                }
            }
        }
    }

    private fun isTestFile(fileName: String): Boolean {
        return fileName.endsWith("Test.kt") ||
               fileName.endsWith("Tests.kt") ||
               fileName.endsWith("Spec.kt") ||
               fileName.contains("test", ignoreCase = true)
    }
}
