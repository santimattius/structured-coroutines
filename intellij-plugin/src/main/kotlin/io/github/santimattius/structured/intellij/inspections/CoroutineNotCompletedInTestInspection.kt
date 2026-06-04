/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/** [TEST_006] — assertion in [runTest] without [advanceUntilIdle] after work that may launch coroutines. */
class CoroutineNotCompletedInTestInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.coroutine.not.completed.in.test.display.name"
    override val descriptionKey = "inspection.coroutine.not.completed.in.test.description"

    private val assertionPrefixes = setOf("assert", "verify", "expect")

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!isAssertionCall(expression)) return
                val runTestCall = findEnclosingRunTest(expression) ?: return
                if (hasAdvanceBeforeAssertion(runTestCall, expression)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.test.coroutine.not.completed"),
                )
            }
        }

    private fun isAssertionCall(call: KtCallExpression): Boolean {
        val name = call.calleeExpression?.text ?: return false
        return assertionPrefixes.any { name.startsWith(it, ignoreCase = true) }
    }

    private fun findEnclosingRunTest(element: KtElement): KtCallExpression? {
        val file = element.containingKtFile
        if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return null
        if (!CoroutinePsiUtils.looksLikeJvmTestKotlinFile(file)) return null
        var current: KtElement? = element
        while (current != null) {
            if (current is KtCallExpression && current.calleeExpression?.text == "runTest") {
                return current
            }
            current = current.parent as? KtElement
        }
        return null
    }

    private fun hasAdvanceBeforeAssertion(runTestCall: KtCallExpression, assertion: KtCallExpression): Boolean {
        val body = runTestCall.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
            ?: return false
        val descendants = body.collectDescendantsOfType<KtCallExpression>()
        val assertionIndex = descendants.indexOf(assertion)
        if (assertionIndex <= 0) return false
        return descendants.subList(0, assertionIndex).any { call ->
            val name = call.calleeExpression?.text
            name == "advanceUntilIdle" || name == "advanceTimeBy"
        }
    }
}
