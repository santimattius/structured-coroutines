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
import io.github.santimattius.structured.intellij.quickfixes.AddCatchOperatorQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * [FLOW_005] — Flow chain is missing a `.catch {}` operator.
 *
 * Detects `.collect { }`, `.collectLatest { }`, or `.launchIn(scope)` calls that are
 * preceded by at least one intermediate operator (`.map`, `.filter`, `.flatMapLatest`, etc.)
 * without a `.catch { }` in the chain.
 *
 * Exclusions:
 * - Chains inside `try/catch(Throwable/Exception)` blocks.
 * - Chains that already have `.catch {}` in the expression.
 * - Test files (paths containing `/test/` or `/androidTest/`).
 */
class MissingCatchInFlowInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.missing.catch.in.flow.display.name"
    override val descriptionKey = "inspection.missing.catch.in.flow.description"

    private val terminalOperators = setOf("collect", "collectLatest", "launchIn")
    private val intermediateOperators = setOf(
        "map", "filter", "flatMapLatest", "flatMapMerge", "flatMapConcat",
        "onEach", "transform", "mapLatest", "filterNot", "filterIsInstance",
        "take", "drop", "debounce", "distinctUntilChanged", "combine",
        "zip", "merge", "buffer", "conflate", "flowOn"
    )

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val calleeName = expression.calleeExpression?.text ?: return
                if (calleeName !in terminalOperators) return

                val file = expression.containingKtFile
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return

                // Exclusion: test files
                val path = file.virtualFilePath.replace('\\', '/')
                if (path.contains("/test/") || path.contains("/androidTest/") ||
                    path.contains("/commonTest/") || path.contains("/iosTest/")) return

                // The terminal operator must be a dot-qualified expression receiver
                val dotExpr = expression.parent as? KtDotQualifiedExpression ?: return

                // Walk back the dot chain to find intermediate operators and check for .catch {}
                if (!hasIntermediateOperator(dotExpr)) return
                if (hasCatchInChain(dotExpr)) return

                // Exclusion: inside try/catch(Exception) or try/catch(Throwable)
                if (isInsideBroadCatch(expression)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.flow.missing.catch.in.flow"),
                    AddCatchOperatorQuickFix()
                )
            }
        }

    /**
     * Checks if the dot-qualified chain (ending at the terminal operator call) contains
     * at least one intermediate Flow operator.
     */
    private fun hasIntermediateOperator(terminalDotExpr: KtDotQualifiedExpression): Boolean {
        var receiver = terminalDotExpr.receiverExpression
        while (receiver is KtDotQualifiedExpression) {
            val selectorCall = receiver.selectorExpression as? KtCallExpression
            val name = selectorCall?.calleeExpression?.text ?: ""
            if (name in intermediateOperators) return true
            receiver = receiver.receiverExpression
        }
        // Also check if the outermost receiver itself is an intermediate call
        val receiverCall = receiver as? KtCallExpression
        return receiverCall?.calleeExpression?.text in intermediateOperators
    }

    /**
     * Checks if `.catch` appears anywhere in the dot-qualified chain.
     */
    private fun hasCatchInChain(dotExpr: KtDotQualifiedExpression): Boolean {
        var receiver: Any? = dotExpr.receiverExpression
        while (receiver is KtDotQualifiedExpression) {
            val selectorCall = receiver.selectorExpression as? KtCallExpression
            if (selectorCall?.calleeExpression?.text == "catch") return true
            receiver = receiver.receiverExpression
        }
        return false
    }

    /**
     * Returns true if [element] is inside a `try` block whose catch clause catches
     * `Exception` or `Throwable` broadly.
     */
    private fun isInsideBroadCatch(element: KtCallExpression): Boolean {
        val tryExpr = element.getParentOfType<KtTryExpression>(strict = true) ?: return false
        return tryExpr.catchClauses.any { clause ->
            val typeText = clause.catchParameter?.typeReference?.text ?: ""
            typeText == "Exception" || typeText == "Throwable" ||
                typeText.endsWith(".Exception") || typeText.endsWith(".Throwable")
        }
    }
}
