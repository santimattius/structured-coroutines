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

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
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
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument
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
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "async") return
        // kotlinx async always takes a block; a call without arguments is an unrelated method (e.g. Lettuce connection.async())
        if (expression.valueArguments.isEmpty()) return

        val prop = expression.getParentOfType<KtProperty>(strict = false) ?: return
        if (flowsToAwait(expression)) return
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
            val selectorStartsAwait = dq.selectorExpression?.text?.startsWith("await") == true
            val receiverMatches = receiverName == varName || receiverName.endsWith(".$varName")
            if (receiverMatches && selectorStartsAwait) return true
        }
        val calls = block.collectDescendantsOfType<KtCallExpression>()
        for (call in calls) {
            if (call === excludeCall) continue
            if (call.calleeExpression?.text == "awaitAll") {
                // awaitAll(varName) or awaitAll(list, ...)
                for (arg in call.valueArguments) {
                    val argText = arg.getArgumentExpression()?.text ?: continue
                    if (argText == varName || argText.endsWith(".$varName")) return true
                }
                // list.awaitAll() — receiver of extension call
                val parent = call.parent as? KtDotQualifiedExpression ?: continue
                val receiverText = parent.receiverExpression.text
                if (receiverText == varName || receiverText.endsWith(".$varName")) return true
            }
        }
        return false
    }

    // Climbs from the async call while its value keeps flowing outward (call chains, call arguments,
    // lambda results, local vals) and stops at the first await/awaitAll. Intermediate call names
    // don't matter: types guarantee that whatever reaches awaitAll() is a collection of Deferreds.
    private fun flowsToAwait(start: KtExpression): Boolean {
        var current: KtExpression = start
        while (true) {
            val parent = current.parent
            current = when {
                parent is KtQualifiedExpression && parent.selectorExpression == current -> parent
                parent is KtQualifiedExpression && parent.receiverExpression == current -> {
                    val selector = parent.selectorExpression as? KtCallExpression
                    if (selector?.calleeExpression?.text in AWAIT_CALLS) return true
                    parent
                }
                parent is KtParenthesizedExpression -> parent
                parent is KtValueArgument -> {
                    val call = parent.getParentOfType<KtCallExpression>(strict = true) ?: return false
                    if (call.calleeExpression?.text == "awaitAll") return true
                    call
                }
                parent is KtBlockExpression && parent.statements.lastOrNull() == current ->
                    lambdaOwnerCall(parent) ?: return false
                parent is KtProperty && parent.initializer == current -> return variableFlowsToAwait(parent)
                else -> return false
            }
        }
    }

    // Name-based like isDeferredUsed; terminates because each hop moves to a later declaration.
    private fun variableFlowsToAwait(property: KtProperty): Boolean {
        val name = property.name ?: return false
        val scope = property.parent as? KtBlockExpression ?: return false
        return scope.collectDescendantsOfType<KtNameReferenceExpression> {
            it.getReferencedName() == name && it.textOffset > property.textOffset
        }.any { flowsToAwait(it) }
    }

    private fun lambdaOwnerCall(lambdaBody: KtBlockExpression): KtCallExpression? {
        val lambda = (lambdaBody.parent as? KtFunctionLiteral)?.parent as? KtLambdaExpression ?: return null
        return when (val holder = lambda.parent) {
            is KtLambdaArgument -> holder.parent as? KtCallExpression
            is KtValueArgument -> holder.parent?.parent as? KtCallExpression
            else -> null
        }
    }

    private companion object {
        val AWAIT_CALLS = setOf("await", "awaitAll")
    }
}
