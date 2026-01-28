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
import io.github.santimattius.structured.intellij.quickfixes.RemoveRunBlockingQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects runBlocking calls inside suspend functions.
 *
 * Calling runBlocking inside a suspend function blocks the thread and defeats
 * the purpose of coroutines. runBlocking is meant to be used only at the top
 * level (main functions, test functions) to bridge blocking and non-blocking code.
 *
 * Example of problematic code:
 * ```kotlin
 * suspend fun fetchData() {
 *     runBlocking {  // Blocks the thread!
 *         delay(1000)
 *         loadFromNetwork()
 *     }
 * }
 * ```
 *
 * Recommended fix:
 * ```kotlin
 * suspend fun fetchData() {
 *     delay(1000)  // Non-blocking
 *     loadFromNetwork()
 * }
 * ```
 */
class RunBlockingInSuspendInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.run.blocking.display.name"
    override val descriptionKey = "inspection.run.blocking.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                // Check if this is a runBlocking call
                if (!CoroutinePsiUtils.isRunBlockingCall(expression)) return

                // Check if we're inside a suspend function
                if (CoroutinePsiUtils.isInSuspendFunction(expression)) {
                    val containingFunction = CoroutinePsiUtils.getContainingFunction(expression)

                    // Allow runBlocking in main() and test functions
                    if (containingFunction != null) {
                        if (CoroutinePsiUtils.isMainFunction(containingFunction) ||
                            CoroutinePsiUtils.isTestFunction(containingFunction)) {
                            return
                        }
                    }

                    holder.registerProblem(
                        expression,
                        StructuredCoroutinesBundle.message("error.run.blocking.in.suspend"),
                        RemoveRunBlockingQuickFix()
                    )
                }
            }
        }
    }
}
