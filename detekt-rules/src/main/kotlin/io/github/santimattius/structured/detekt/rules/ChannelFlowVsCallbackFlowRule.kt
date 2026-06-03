/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

/**
 * [INTEROP_003] — choose [callbackFlow] vs [channelFlow] correctly.
 */
class ChannelFlowVsCallbackFlowRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ChannelFlowVsCallbackFlow",
        severity = Severity.Warning,
        description = "[INTEROP_003] Use callbackFlow for external callbacks (with awaitClose); channelFlow for internal emission. " +
            "See: ${DetektDocUrl.buildDocLink("103-interop_003--channelflow-vs-callbackflow")}",
        debt = Debt.FIVE_MINS,
    )

    private val externalCallbackCalleePattern = Regex(
        """(?i)(register|unregister|addListener|removeListener|subscribe|unsubscribe|setCallback|observe|enqueue)""",
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementImportsCoroutinesOrFlow(expression)) return

        val calleeName = expression.calleeExpression?.text ?: return
        val lambdaExpr = extractTrailingLambda(expression) ?: return

        when (calleeName) {
            "channelFlow" -> {
                if (lambdaBodyContainsAwaitClose(lambdaExpr)) return
                reportMismatch(
                    expression,
                    "channelFlow without awaitClose — external callbacks need callbackFlow { awaitClose { } }",
                )
            }
            "callbackFlow" -> {
                if (!lambdaBodyContainsAwaitClose(lambdaExpr)) return
                if (lambdaHasExternalCallbackRegistration(lambdaExpr)) return
                reportMismatch(
                    expression,
                    "callbackFlow with no external callback — use channelFlow for internal multi-coroutine emission",
                )
            }
        }
    }

    private fun reportMismatch(expression: KtCallExpression, detail: String) {
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[INTEROP_003] $detail. " +
                    "See: ${DetektDocUrl.buildDocLink("103-interop_003--channelflow-vs-callbackflow")}",
            ),
        )
    }

    private fun extractTrailingLambda(expression: KtCallExpression): KtLambdaExpression? {
        expression.lambdaArguments.lastOrNull()?.getArgumentExpression()?.let { arg ->
            if (arg is KtLambdaExpression) return arg
        }
        return null
    }

    private fun lambdaBodyContainsAwaitClose(lambda: KtLambdaExpression): Boolean =
        lambda.bodyExpression?.anyDescendantOfType<KtCallExpression> { call ->
            call.calleeExpression?.text == "awaitClose"
        } ?: false

    private fun lambdaHasExternalCallbackRegistration(lambda: KtLambdaExpression): Boolean =
        lambda.bodyExpression?.anyDescendantOfType<KtCallExpression> { call ->
            val name = call.calleeExpression?.text.orEmpty()
            externalCallbackCalleePattern.containsMatchIn(name)
        } ?: false
}
