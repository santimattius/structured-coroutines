/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Detekt rule that detects GlobalScope usage.
 *
 * ## Problem (Best Practice 1.1)
 *
 * GlobalScope bypasses structured concurrency and can lead to resource leaks.
 * Coroutines launched on GlobalScope are not tied to any lifecycle and will run
 * until completion regardless of cancellation.
 *
 * ```kotlin
 * // ❌ BAD: GlobalScope usage
 * GlobalScope.launch {
 *     fetchData()  // This coroutine will run even if the component is destroyed
 * }
 *
 * GlobalScope.async {
 *     computeValue()  // No way to cancel this from outside
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Use structured scopes that are tied to a lifecycle:
 *
 * ```kotlin
 * // ✅ GOOD: Framework scopes
 * class MyViewModel : ViewModel() {
 *     fun load() {
 *         viewModelScope.launch { fetchData() }
 *     }
 * }
 *
 * // ✅ GOOD: Annotated structured scope
 * fun process(@StructuredScope scope: CoroutineScope) {
 *     scope.launch { fetchData() }
 * }
 *
 * // ✅ GOOD: Structured builders
 * suspend fun process() = coroutineScope {
 *     launch { fetchData() }
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   GlobalScopeUsage:
 *     active: true
 *     severity: error  # or warning
 * ```
 */
class GlobalScopeUsageRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "GlobalScopeUsage",
        severity = Severity.CodeSmell,
        description = "GlobalScope usage bypasses structured concurrency and can lead to resource leaks. " +
            "Use framework scopes (viewModelScope, lifecycleScope), @StructuredScope annotated scopes, " +
            "or structured builders (coroutineScope, supervisorScope).",
        debt = Debt.TEN_MINS
    )

    private val coroutineBuilders = setOf("launch", "async")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val builderName = expression.calleeExpression?.text ?: return
        if (builderName !in coroutineBuilders) return

        // Check if receiver is GlobalScope
        // Get receiver from parent DotQualifiedExpression
        val parent = expression.parent
        val receiver = if (parent is KtDotQualifiedExpression) {
            parent.receiverExpression
        } else {
            null
        }
        if (receiver != null && isGlobalScope(receiver)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = buildMessage(builderName)
                )
            )
        }
    }

    private fun isGlobalScope(receiver: KtExpression): Boolean {
        return when (receiver) {
            is KtNameReferenceExpression -> {
                receiver.text == "GlobalScope"
            }
            is KtDotQualifiedExpression -> {
                // Handle cases like kotlinx.coroutines.GlobalScope
                val selector = receiver.selectorExpression?.text
                selector == "GlobalScope"
            }
            else -> false
        }
    }

    private fun buildMessage(builderName: String): String {
        return "GlobalScope.$builderName bypasses structured concurrency. " +
            "Use framework scopes (viewModelScope, lifecycleScope), @StructuredScope annotated scopes, " +
            "or structured builders (coroutineScope { }, supervisorScope { })."
    }
}
