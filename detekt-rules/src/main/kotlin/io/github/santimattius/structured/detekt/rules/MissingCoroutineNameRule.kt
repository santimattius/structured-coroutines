/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
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
 * [DEBUG_001] — unnamed [launch]/[async] (opt-in via Detekt `active: false` by default).
 */
class MissingCoroutineNameRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "MissingCoroutineName",
        severity = Severity.Minor,
        description = "[DEBUG_001] Add CoroutineName(\"descriptor\") for easier debugging. " +
            "See: ${DetektDocUrl.buildDocLink("141-debug_001--missing-coroutine-name")}",
        debt = Debt.FIVE_MINS,
    )

    private val builderNames = setOf("launch", "async")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return

        val callee = expression.calleeExpression?.text ?: return
        if (callee !in builderNames) return
        if (callHasCoroutineName(expression)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[DEBUG_001] Unnamed coroutine — add CoroutineName(\"descriptor\") to the context " +
                    "for clearer stack traces. See: ${DetektDocUrl.buildDocLink("141-debug_001--missing-coroutine-name")}",
            ),
        )
    }

    private fun callHasCoroutineName(call: KtCallExpression): Boolean {
        val argsText = call.valueArguments.joinToString { it.getArgumentExpression()?.text.orEmpty() }
        return argsText.contains("CoroutineName")
    }
}
