/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression

/**
 * [CONCUR_004] — nested withContext using the same dispatcher reference (opt-in / inactive by default in profiles).
 */
class RedundantWithContextRule(config: Config = Config.empty) : Rule(
    config,
    description = "[CONCUR_004] Nested withContext with the same dispatcher is redundant. " +
            "See: ${DetektDocUrl.buildDocLink("36-concur_004--redundantwithcontext")}",
) {

    override val ruleName: RuleName get() = RuleName("RedundantWithContext")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        if (expression.calleeExpression?.text != "withContext") return

        val outerDispatcher = expression.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return
        val innerLambda = expression.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
        val innerWithContext = findNestedWithContext(innerLambda) ?: return
        val innerDispatcher = innerWithContext.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return
        if (outerDispatcher != innerDispatcher) return

        report(
            Finding(
                entity = Entity.from(innerWithContext),
                message = "[CONCUR_004] Redundant nested withContext($outerDispatcher) — already on that dispatcher. " +
                    "See: ${DetektDocUrl.buildDocLink("36-concur_004--redundantwithcontext")}",
            ),
        )
    }

    private fun findNestedWithContext(lambda: KtLambdaExpression): KtCallExpression? {
        val body = lambda.bodyExpression ?: return null
        val firstStatement = body.statements.firstOrNull() as? KtCallExpression
        return firstStatement?.takeIf { it.calleeExpression?.text == "withContext" }
    }
}
