/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.github.santimattius.structured.detekt.utils.SideEffectInMapHeuristic
import dev.detekt.api.CodeSmell
import dev.detekt.api.Config
import dev.detekt.api.Debt
import dev.detekt.api.Entity
import dev.detekt.api.Issue
import dev.detekt.api.Rule
import dev.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * [FLOW_008] — side effects inside Flow.map (inactive by default in consumer profiles).
 */
class SideEffectInMapOperatorRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "SideEffectInMapOperator",
        severity = Severity.Minor,
        description = "[FLOW_008] Side effect inside map — use onEach for effects. " +
            "See: ${DetektDocUrl.buildDocLink("99-flow_008--sideeffectinmapoperator")}",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        if (expression.calleeExpression?.text != "map") return
        val lambda = expression.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return
        if (!SideEffectInMapHeuristic.hasSideEffectBeforeReturn(lambda)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[FLOW_008] Side effect detected inside .map { } — use .onEach { } for effects. " +
                    "See: ${DetektDocUrl.buildDocLink("99-flow_008--sideeffectinmapoperator")}",
            ),
        )
    }
}
