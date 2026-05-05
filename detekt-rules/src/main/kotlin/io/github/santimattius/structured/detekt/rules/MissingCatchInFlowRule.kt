/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.github.santimattius.structured.detekt.utils.MissingCatchInFlowHeuristic
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * [FLOW_005] — upstream `.catch { }` recommended before terminal `.collect` / `.launchIn`
 * on transformed Flow chains (same heuristic as Lint `MissingCatchInFlow`).
 */
class MissingCatchInFlowRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "MissingCatchInFlow",
        severity = Severity.CodeSmell,
        description = "[FLOW_005] Transformed Flow chains often need `.catch { }` before the terminal collector " +
            "to avoid cancelling the enclosing scope unexpectedly. See: ${DetektDocUrl.buildDocLink("96-flow_005--missing-catch-in-flow-chain")}",
        debt = Debt.TEN_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementImportsCoroutinesOrFlow(expression)) return

        val fileName = expression.containingKtFile.name
        val path = expression.containingKtFile.virtualFilePath
        if (CoroutineDetektUtils.isTestFile(fileName) || CoroutineDetektUtils.isTestFile(path)) return

        val callee = expression.calleeExpression?.text ?: return
        if (callee !in setOf("collect", "collectLatest", "launchIn")) return

        if (!MissingCatchInFlowHeuristic.shouldReportTerminalCall(expression)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[FLOW_005] Consider `.catch { }` before `.${callee}` on this Flow chain unless errors " +
                    "are deliberately propagated. See: ${DetektDocUrl.buildDocLink("96-flow_005--missing-catch-in-flow-chain")}",
            ),
        )
    }
}
