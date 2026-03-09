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
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Heuristic rule that detects `withTimeout` calls not wrapped in a try/catch that
 * handles `TimeoutCancellationException` (or a parent type such as `CancellationException`,
 * `Exception`, or `Throwable`).
 *
 * ## Problem (Best Practice 4.6 - CANCEL_006)
 *
 * `withTimeout` throws `TimeoutCancellationException` when the timeout expires.
 * If that exception propagates uncaught it cancels the **parent scope**, which may
 * silently kill sibling coroutines. Prefer `withTimeoutOrNull` to get a `null`
 * result without affecting the scope, or catch `TimeoutCancellationException`
 * explicitly when using `withTimeout`.
 *
 * ## False positives
 *
 * This rule reports `withTimeout` even when the caller intentionally wants the
 * parent scope to be cancelled on timeout. In that case suppress the finding:
 * ```kotlin
 * @Suppress("WithTimeoutScopeCancellation")
 * withTimeout(5_000) { ... }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   WithTimeoutScopeCancellation:
 *     active: true
 * ```
 */
class WithTimeoutScopeCancellationRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "WithTimeoutScopeCancellation",
        severity = Severity.Warning,
        description = "[CANCEL_006] withTimeout without handling TimeoutCancellationException may cancel the parent scope. " +
            "Prefer withTimeoutOrNull or catch TimeoutCancellationException explicitly. " +
            "See: ${DetektDocUrl.buildDocLink("46-cancel_006--withtimeout-and-scope-cancellation")}",
        debt = Debt.FIVE_MINS
    )

    /**
     * Exception type names that cover `TimeoutCancellationException`.
     * Any of these in a catch clause means the timeout is properly handled.
     */
    private val handledExceptions = setOf(
        "TimeoutCancellationException",
        "CancellationException",
        "Exception",
        "Throwable",
        "java.lang.Exception",
        "java.lang.Throwable",
        "kotlinx.coroutines.TimeoutCancellationException",
        "kotlin.coroutines.cancellation.CancellationException",
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val callee = expression.calleeExpression?.text ?: return
        if (callee != "withTimeout") return

        // Walk up to the nearest try expression (stopping at a function boundary)
        val tryExpression = expression.getParentOfType<KtTryExpression>(strict = true)
        if (tryExpression != null && catchesTimeoutException(tryExpression)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[CANCEL_006] withTimeout may cancel the parent scope if TimeoutCancellationException propagates. " +
                    "Use withTimeoutOrNull or wrap in try { } catch (e: TimeoutCancellationException) { }. " +
                    "See: ${DetektDocUrl.buildDocLink("46-cancel_006--withtimeout-and-scope-cancellation")}"
            )
        )
    }

    private fun catchesTimeoutException(tryExpression: KtTryExpression): Boolean {
        return tryExpression.catchClauses.any { clause ->
            val typeName = clause.catchParameter?.typeReference?.text
            typeName != null && typeName in handledExceptions
        }
    }
}
