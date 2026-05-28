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
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * [CONCUR_001] — `synchronized` blocks the dispatcher thread inside coroutines; prefer `Mutex.withLock`.
 */
class SynchronizedInCoroutineRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "SynchronizedInCoroutine",
        severity = Severity.Warning,
        description = "[CONCUR_001] synchronized() inside a coroutine blocks the dispatcher thread. " +
            "Use Mutex.withLock { } instead. " +
            "See: ${DetektDocUrl.buildDocLink("121-concur_001--synchronizedincoroutine")}",
        debt = Debt.TEN_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return

        if (expression.calleeExpression?.text != "synchronized") return
        if (!CoroutineDetektUtils.isInsideCoroutine(expression)) return
        if (isInsideMutexWithLock(expression)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[CONCUR_001] synchronized() inside a coroutine blocks the dispatcher thread and can " +
                    "deadlock on single-thread dispatchers. Use Mutex.withLock { } instead. " +
                    "See: ${DetektDocUrl.buildDocLink("121-concur_001--synchronizedincoroutine")}",
            ),
        )
    }

    private fun isInsideMutexWithLock(element: KtElement): Boolean {
        var current: KtElement? = element
        while (current != null) {
            val lambda = current.getParentOfType<KtLambdaExpression>(strict = true) ?: run {
                current = current.parent as? KtElement
                continue
            }
            val parent = lambda.parent
            val call = when (parent) {
                is KtCallExpression -> parent
                is KtLambdaArgument -> parent.parent?.parent as? KtCallExpression
                else -> null
            }
            if (call?.calleeExpression?.text == "withLock") {
                return true
            }
            current = lambda.parent as? KtElement
        }
        return false
    }
}
