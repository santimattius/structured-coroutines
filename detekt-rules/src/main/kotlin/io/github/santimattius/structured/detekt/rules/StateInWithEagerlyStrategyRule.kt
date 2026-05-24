/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

/**
 * [FLOW_006] — `SharingStarted.Eagerly` in lifecycle scopes starts collection before subscribers exist.
 */
class StateInWithEagerlyStrategyRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "StateInWithEagerlyStrategy",
        severity = Severity.Warning,
        description = "[FLOW_006] stateIn with SharingStarted.Eagerly in a lifecycle scope starts work " +
            "before any collector is active. Prefer SharingStarted.WhileSubscribed(5_000). " +
            "See: ${DetektDocUrl.buildDocLink("97-flow_006--stateinwitheagerlystrategy")}",
        debt = Debt.FIVE_MINS,
    )

    private val lifecycleScopeNames = setOf("viewModelScope", "lifecycleScope")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        if (expression.calleeExpression?.text != "stateIn") return

        val args = expression.valueArguments.mapNotNull { it.getArgumentExpression()?.text }
        val usesEagerly = args.any { it.contains("SharingStarted.Eagerly") || it.contains("Eagerly") }
        if (!usesEagerly) return

        val scopeArg = expression.valueArguments.getOrNull(0)?.getArgumentExpression()?.text ?: return
        val isLifecycleScope = lifecycleScopeNames.any { scopeArg.contains(it) }
        if (!isLifecycleScope) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[FLOW_006] stateIn(..., SharingStarted.Eagerly, ...) on $scopeArg starts upstream " +
                    "collection immediately. Prefer SharingStarted.WhileSubscribed(5_000) for rotation safety. " +
                    "See: ${DetektDocUrl.buildDocLink("97-flow_006--stateinwitheagerlystrategy")}",
            ),
        )
    }
}
