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
import io.github.santimattius.structured.intellij.quickfixes.WrapWithNonCancellableQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects suspend function calls in finally blocks
 * without NonCancellable protection.
 *
 * When a coroutine is cancelled, suspend calls in finally blocks may not
 * complete because the coroutine's Job is already cancelled. To ensure
 * cleanup code runs, wrap suspend calls with withContext(NonCancellable).
 *
 * Example of problematic code:
 * ```kotlin
 * try {
 *     doWork()
 * } finally {
 *     saveToDatabase()  // May not complete if cancelled!
 * }
 * ```
 *
 * Recommended fix:
 * ```kotlin
 * try {
 *     doWork()
 * } finally {
 *     withContext(NonCancellable) {
 *         saveToDatabase()  // Will complete even if cancelled
 *     }
 * }
 * ```
 */
class SuspendInFinallyInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.suspend.in.finally.display.name"
    override val descriptionKey = "inspection.suspend.in.finally.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.callIsInCoroutinesFile(expression)) return

                // Only check if we're inside a suspend context
                if (!CoroutinePsiUtils.isInsideCoroutineContext(expression) &&
                    !CoroutinePsiUtils.isInSuspendFunction(expression)) {
                    return
                }

                // Check if we're in a finally block
                if (!CoroutinePsiUtils.isInFinallyBlock(expression)) return

                // Check if already wrapped in NonCancellable
                if (CoroutinePsiUtils.isWrappedInNonCancellable(expression)) return

                // Check if this looks like a suspend call
                if (isPotentiallySuspendCall(expression)) {
                    holder.registerProblem(
                        expression,
                        StructuredCoroutinesBundle.message("error.suspend.in.finally"),
                        WrapWithNonCancellableQuickFix()
                    )
                }
            }
        }
    }

    private fun isPotentiallySuspendCall(call: KtCallExpression): Boolean {
        // Delegate entirely to isSuspendCall() which uses PSI resolve + COOPERATION_POINTS.
        // The previous heuristic (suspectPatterns: save, load, fetch, etc.) produced
        // false positives on any regular function whose name contained those substrings.
        return CoroutinePsiUtils.isSuspendCall(call)
    }
}
