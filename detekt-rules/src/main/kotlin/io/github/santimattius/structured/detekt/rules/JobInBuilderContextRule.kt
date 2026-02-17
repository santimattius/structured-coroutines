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
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * Detekt rule that detects Job() or SupervisorJob() passed directly to coroutine builders.
 *
 * ## Problem (Best Practice 3.4 / 5.1)
 *
 * Passing a new Job directly to launch/async/withContext breaks the parent-child
 * relationship that is fundamental to structured concurrency.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   JobInBuilderContext:
 *     active: true
 * ```
 */
class JobInBuilderContextRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "JobInBuilderContext",
        severity = Severity.Warning,
        description = "[DISPATCH_004] Job() or SupervisorJob() passed to coroutine builder breaks structured concurrency. " +
            "Use supervisorScope { } or the scope's default Job. " +
            "See: ${DetektDocUrl.buildDocLink("34-dispatch_004--passing-job-directly-as-context-to-builders")}",
        debt = Debt.TEN_MINS
    )

    private val builders = setOf("launch", "async", "withContext")
    private val jobConstructors = setOf("Job", "SupervisorJob")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName !in builders) return

        for (arg in expression.valueArguments) {
            if (isJobOrSupervisorJobCall(arg)) {
                report(
                    CodeSmell(
                        issue = issue,
                        entity = Entity.from(expression),
                        message = "[DISPATCH_004] Passing Job() or SupervisorJob() to $calleeName breaks structured concurrency. " +
                            "Use supervisorScope { } for supervision, or omit the context to use the parent's Job. " +
                            "See: ${DetektDocUrl.buildDocLink("34-dispatch_004--passing-job-directly-as-context-to-builders")}"
                    )
                )
                return
            }
        }
    }

    private fun isJobOrSupervisorJobCall(arg: KtValueArgument): Boolean {
        val expr = arg.getArgumentExpression() ?: return false
        return when (expr) {
            is KtCallExpression -> expr.calleeExpression?.text in jobConstructors
            else -> {
                // Handle qualified refs like kotlinx.coroutines.Job()
                val text = expr.text
                jobConstructors.any { text.endsWith(".$it()") || text == "$it()" }
            }
        }
    }
}
