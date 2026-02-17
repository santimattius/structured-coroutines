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
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Detekt rule that detects suspend calls in finally blocks without withContext(NonCancellable).
 *
 * ## Problem (Best Practice 4.4)
 *
 * Suspend calls in finally may not run if the coroutine is cancelled; wrap in withContext(NonCancellable).
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   SuspendInFinally:
 *     active: true
 * ```
 */
class SuspendInFinallyRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "SuspendInFinally",
        severity = Severity.Warning,
        description = "[CANCEL_004] Suspend call in finally block may not execute when cancelled. " +
            "Wrap in withContext(NonCancellable) { }. " +
            "See: ${DetektDocUrl.buildDocLink("44-cancel_004--suspendable-cleanup-without-noncancellable")}",
        debt = Debt.TEN_MINS
    )

    private val knownSuspendNames = setOf(
        "delay", "yield", "withContext", "withTimeout", "withTimeoutOrNull",
        "await", "awaitAll", "join", "joinAll", "cancelAndJoin",
        "suspendCancellableCoroutine", "suspendCoroutine", "coroutineScope", "supervisorScope"
    )

    override fun visitTryExpression(expression: KtTryExpression) {
        super.visitTryExpression(expression)
        val finallySection = expression.finallyBlock ?: return
        val body = finallySection.finalExpression ?: return

        findUnprotectedSuspendCalls(body).firstOrNull()?.let { call ->
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(call),
                    message = "[CANCEL_004] Suspend call in finally without NonCancellable. " +
                        "Wrap cleanup in withContext(NonCancellable) { } so it runs even when cancelled. " +
                        "See: ${DetektDocUrl.buildDocLink("44-cancel_004--suspendable-cleanup-without-noncancellable")}"
                )
            )
        }
    }

    private fun findUnprotectedSuspendCalls(element: KtElement): List<KtCallExpression> {
        val result = mutableListOf<KtCallExpression>()
        val calls = element.collectDescendantsOfType<KtCallExpression>()
        for (call in calls) {
            if (isWithContextNonCancellable(call)) continue
            if (isInsideNonCancellableBlock(call)) continue
            val callee = call.calleeExpression?.text ?: continue
            if (callee in knownSuspendNames) result.add(call)
        }
        return result
    }

    private fun isInsideNonCancellableBlock(call: KtCallExpression): Boolean {
        var current: KtElement? = call.parent as? KtElement
        while (current != null) {
            if (current is KtCallExpression && isWithContextNonCancellable(current)) return true
            current = current.parent as? KtElement
        }
        return false
    }

    private fun isWithContextNonCancellable(call: KtCallExpression): Boolean {
        if (call.calleeExpression?.text != "withContext") return false
        val firstArg = call.valueArguments.firstOrNull() ?: return false
        val exprText = firstArg.getArgumentExpression()?.text ?: return false
        return exprText.contains("NonCancellable")
    }
}
