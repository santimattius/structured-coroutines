/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * [TEST_004] — `@Test fun … = runBlocking { … }` should prefer `runTest` from kotlinx-coroutines-test.
 */
class RunBlockingInsteadOfRunTestInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.runblocking.instead.runtest.display.name"
    override val descriptionKey = "inspection.runblocking.instead.runtest.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                val file = function.containingKtFile
                if (!CoroutinePsiUtils.looksLikeJvmTestKotlinFile(file)) return
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return
                if (!function.hasJvmTestNamedAnnotation()) return

                val bodyExpr = function.bodyExpression as? KtCallExpression ?: return
                if (bodyExpr.calleeExpression?.text != "runBlocking") return
                if (bodyExpr.runBlockingLambdaSubtreeContainsDelay()) return

                holder.registerProblem(
                    bodyExpr,
                    StructuredCoroutinesBundle.message("error.test.runblocking.instead.runtest"),
                )
            }
        }

    private fun KtNamedFunction.hasJvmTestNamedAnnotation(): Boolean =
        annotationEntries.any { it.shortName?.asString() == "Test" }

    /** If `delay(...)` sits under this `runBlocking`, defer to TEST_001 (RunBlockingWithDelayInTest). */
    private fun KtCallExpression.runBlockingLambdaSubtreeContainsDelay(): Boolean =
        collectDescendantsOfType<KtCallExpression>()
            .any { it !== this && it.calleeExpression?.text == "delay" }
}
