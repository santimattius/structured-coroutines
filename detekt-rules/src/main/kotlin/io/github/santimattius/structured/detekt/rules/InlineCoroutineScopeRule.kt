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
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVariableDeclaration

/**
 * Detekt rule that detects inline CoroutineScope creation.
 *
 * ## Problem (Best Practice 1.3)
 *
 * Creating a CoroutineScope inline and immediately launching on it creates an orphan
 * coroutine that isn't tied to any lifecycle. This breaks structured concurrency.
 *
 * ```kotlin
 * // ❌ BAD: Inline scope creation
 * CoroutineScope(Dispatchers.IO).launch {
 *     fetchData()  // Orphan coroutine with no parent
 * }
 *
 * CoroutineScope(Job()).async {
 *     computeValue()  // No lifecycle management
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Use properly managed scopes:
 *
 * ```kotlin
 * // ✅ GOOD: Annotated structured scope
 * class Repository(@StructuredScope private val scope: CoroutineScope) {
 *     fun fetch() {
 *         scope.launch { fetchData() }
 *     }
 * }
 *
 * // ✅ GOOD: Framework scopes
 * class MyViewModel : ViewModel() {
 *     fun load() {
 *         viewModelScope.launch { fetchData() }
 *     }
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
 *   InlineCoroutineScope:
 *     active: true
 *     severity: error  # or warning
 * ```
 */
class InlineCoroutineScopeRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "InlineCoroutineScope",
        severity = Severity.CodeSmell,
        description = "Inline CoroutineScope creation creates orphan coroutines without lifecycle management. " +
            "Use @StructuredScope annotated scopes, framework scopes, or structured builders.",
        debt = Debt.TEN_MINS
    )

    private val coroutineBuilders = setOf("launch", "async")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val builderName = expression.calleeExpression?.text ?: return
        if (builderName !in coroutineBuilders) return

        // Check if receiver is a CoroutineScope constructor call
        // Get receiver from parent DotQualifiedExpression
        val parent = expression.parent
        val receiver = if (parent is KtDotQualifiedExpression) {
            parent.receiverExpression
        } else {
            null
        }
        if (receiver != null && isCoroutineScopeConstructor(receiver)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = buildMessage(builderName)
                )
            )
        }
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        // Check if property initializer is CoroutineScope constructor
        val initializer = property.initializer
        if (initializer is KtCallExpression && isCoroutineScopeConstructor(initializer)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(property),
                    message = "Property '${property.name}' initialized with inline CoroutineScope creation. " +
                        "This creates an orphan coroutine scope without lifecycle management. " +
                        "Use @StructuredScope annotation on a properly managed CoroutineScope, " +
                        "framework scopes (viewModelScope, lifecycleScope), or structured builders."
                )
            )
        }
    }

    private fun isCoroutineScopeConstructor(receiver: KtExpression): Boolean {
        return when (receiver) {
            is KtCallExpression -> {
                val calleeName = receiver.calleeExpression?.text
                calleeName == "CoroutineScope"
            }
            is KtDotQualifiedExpression -> {
                // Handle cases like kotlinx.coroutines.CoroutineScope(...)
                val selector = receiver.selectorExpression
                if (selector is KtCallExpression) {
                    selector.calleeExpression?.text == "CoroutineScope"
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun buildMessage(builderName: String): String {
        return "CoroutineScope(...).$builderName creates an orphan coroutine without lifecycle management. " +
            "Use @StructuredScope on a properly managed CoroutineScope, framework scopes " +
            "(viewModelScope, lifecycleScope), or structured builders (coroutineScope { }, supervisorScope { })."
    }
}
