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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Detekt rule that detects scope.cancel() followed by scope.launch/async in the same function.
 *
 * ## Problem (Best Practice 4.5 - CANCEL_005)
 *
 * A cancelled scope does not accept new children; launch/async after cancel() fails silently.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   ScopeReuseAfterCancel:
 *     active: true
 * ```
 */
class ScopeReuseAfterCancelRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ScopeReuseAfterCancel",
        severity = Severity.Warning,
        description = "[CANCEL_005] Scope cancelled and then reused. " +
            "Use coroutineContext.job.cancelChildren() instead of cancel() if you need to reuse the scope. " +
            "See: ${DetektDocUrl.buildDocLink("45-cancel_005--reusing-a-cancelled-coroutinescope")}",
        debt = Debt.TEN_MINS
    )

    private val builderNames = setOf("launch", "async")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callee = expression.calleeExpression?.text ?: return
        if (callee != "cancel") return

        val scopeName = getReceiverName(expression) ?: return
        val function = expression.getParentOfType<KtNamedFunction>(strict = false) ?: return
        val body = function.bodyExpression ?: return
        val cancelOffset = expression.textOffset

        val hasReuse = body.collectDescendantsOfType<KtCallExpression>().any { call ->
            call.calleeExpression?.text in builderNames &&
                getReceiverName(call) == scopeName &&
                call.textOffset > cancelOffset
        }
        if (hasReuse) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "[CANCEL_005] Scope '$scopeName' is cancelled and then reused. " +
                        "Use cancelChildren() instead of cancel() if you need to reuse the scope. " +
                        "See: ${DetektDocUrl.buildDocLink("45-cancel_005--reusing-a-cancelled-coroutinescope")}"
                )
            )
        }
    }

    private fun getReceiverName(call: KtCallExpression): String? {
        val parent = call.parent as? KtDotQualifiedExpression ?: return null
        return parent.receiverExpression.text
    }
}
