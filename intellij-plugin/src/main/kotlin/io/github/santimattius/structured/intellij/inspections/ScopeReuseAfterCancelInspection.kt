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
import io.github.santimattius.structured.intellij.quickfixes.ReplaceCancelWithCancelChildrenQuickFix
import io.github.santimattius.structured.intellij.utils.ScopeAnalyzer
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects scope cancellation followed by reuse.
 *
 * Cancelling a CoroutineScope and then trying to launch more coroutines
 * in that same scope doesn't work. A scope with a cancelled Job doesn't
 * accept new children, and subsequent launches fail silently.
 *
 * Example of problematic code:
 * ```kotlin
 * fun process(scope: CoroutineScope) {
 *     scope.cancel()
 *     scope.launch { work() }  // Won't work - scope is cancelled
 * }
 * ```
 *
 * Recommended fix:
 * ```kotlin
 * fun process(scope: CoroutineScope) {
 *     scope.coroutineContext.job.cancelChildren()  // Cancel children, keep scope usable
 *     scope.launch { work() }  // Still works
 * }
 * ```
 */
class ScopeReuseAfterCancelInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.scope.reuse.display.name"
    override val descriptionKey = "inspection.scope.reuse.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)

                // Analyze the function for scope reuse after cancel violations
                val violations = ScopeAnalyzer.findScopeReuseAfterCancel(function)

                for (violation in violations) {
                    holder.registerProblem(
                        violation.cancelElement,
                        StructuredCoroutinesBundle.message(
                            "error.scope.reuse.after.cancel",
                            violation.scopeName
                        ),
                        ReplaceCancelWithCancelChildrenQuickFix(violation.scopeName)
                    )
                }
            }
        }
    }
}
