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
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Detekt rule that detects redundant launch inside coroutineScope/supervisorScope.
 *
 * ## Problem (Best Practice 2.1)
 *
 * Using a single launch inside coroutineScope { } is redundant; execute the work directly.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   RedundantLaunchInCoroutineScope:
 *     active: true
 * ```
 */
class RedundantLaunchInCoroutineScopeRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "RedundantLaunchInCoroutineScope",
        severity = Severity.Warning,
        description = "[RUNBLOCK_001] Single launch inside coroutineScope/supervisorScope is redundant. " +
            "Execute the work directly without launch. " +
            "See: ${DetektDocUrl.buildDocLink("21-runblock_001--using-launch-on-the-last-line-of-coroutinescope")}",
        debt = Debt.FIVE_MINS
    )

    private val scopeNames = setOf("coroutineScope", "supervisorScope")
    private val builderNames = setOf("launch", "async")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName !in scopeNames) return

        val lambdaArg = expression.lambdaArguments.firstOrNull() ?: return
        val lambdaExpr = lambdaArg.getArgumentExpression() as? KtLambdaExpression ?: return
        val body = lambdaExpr.bodyExpression ?: return

        val allCalls = body.collectDescendantsOfType<KtCallExpression>()
        var launchCount = 0
        var asyncCount = 0
        for (call in allCalls) {
            val name = call.calleeExpression?.text
            if (name == "launch") launchCount++
            else if (name == "async") asyncCount++
        }
        val totalBuilders = launchCount + asyncCount

        if (launchCount == 1 && totalBuilders == 1) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "[RUNBLOCK_001] Redundant launch inside $calleeName. " +
                        "Execute the work directly: $calleeName { work() } instead of $calleeName { launch { work() } }. " +
                        "See: ${DetektDocUrl.buildDocLink("21-runblock_001--using-launch-on-the-last-line-of-coroutinescope")}"
                )
            )
        }
    }
}
