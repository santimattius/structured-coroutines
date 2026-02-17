/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Detekt rule that detects async() result (Deferred) never awaited.
 *
 * ## Problem (Best Practice 1.2)
 *
 * Using async without await() can hide exceptions; use launch if no result is needed.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   UnusedDeferred:
 *     active: true
 * ```
 */
class UnusedDeferredRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "UnusedDeferred",
        severity = Severity.Warning,
        description = "[SCOPE_002] Deferred from async() is never awaited. " +
            "Call .await() or use launch {} if no result is needed. " +
            "See: ${DetektDocUrl.buildDocLink("12-scope_002--using-async-without-calling-await")}",
        debt = Debt.TEN_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "async") return

        val prop = expression.getParentOfType<KtProperty>(strict = false) ?: return
        val varName = getAssignedVariableName(expression) ?: return
        val block = prop.getParentOfType<KtBlockExpression>(strict = false) ?: return
        if (isDeferredUsed(block, varName, expression)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[SCOPE_002] Deferred from async() is never awaited. " +
                    "Call $varName.await() or use launch { } if no result is needed. " +
                    "See: ${DetektDocUrl.buildDocLink("12-scope_002--using-async-without-calling-await")}"
            )
        )
    }

    private fun getAssignedVariableName(asyncCall: KtCallExpression): String? {
        val prop = asyncCall.getParentOfType<KtProperty>(strict = false) ?: return null
        val initializer = prop.initializer ?: return null
        // Async call may be the initializer (async { }) or inside it (scope.async { })
        var p: org.jetbrains.kotlin.psi.KtElement? = asyncCall.parent as? org.jetbrains.kotlin.psi.KtElement
        while (p != null && p != prop) {
            if (p == initializer) return prop.name
            p = p.parent as? org.jetbrains.kotlin.psi.KtElement
        }
        return if (initializer == asyncCall) prop.name else null
    }

    private fun isDeferredUsed(block: KtBlockExpression, varName: String, excludeCall: KtCallExpression): Boolean {
        val dotQualified = block.collectDescendantsOfType<KtDotQualifiedExpression>()
        for (dq in dotQualified) {
            val receiver = dq.receiverExpression
            val receiverName = when (receiver) {
                is KtNameReferenceExpression -> receiver.getReferencedName()
                else -> receiver.text
            }
            if (receiverName == varName && dq.selectorExpression?.text?.startsWith("await") == true) return true
        }
        val calls = block.collectDescendantsOfType<KtCallExpression>()
        for (call in calls) {
            if (call === excludeCall) continue
            if (call.calleeExpression?.text == "awaitAll") {
                for (arg in call.valueArguments) {
                    val argText = arg.getArgumentExpression()?.text ?: continue
                    if (argText == varName || argText.endsWith(".$varName")) return true
                }
            }
        }
        return false
    }
}
