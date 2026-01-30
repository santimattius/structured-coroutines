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

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Detekt rule that detects launching coroutines on external scopes from suspend functions.
 *
 * ## Problem (Best Practice 1.3)
 *
 * Launching coroutines on external scopes from inside a suspend function breaks
 * structured concurrency because the launched coroutine's lifecycle is not tied
 * to the calling function.
 *
 * ```kotlin
 * // ❌ BAD: External scope launch in suspend function
 * class MyService(private val scope: CoroutineScope) {
 *     suspend fun process() {
 *         scope.launch { work() }  // Not tied to process() lifecycle
 *     }
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Use coroutineScope { } to create child coroutines that respect structured concurrency:
 *
 * ```kotlin
 * // ✅ GOOD: Structured concurrency
 * class MyService {
 *     suspend fun process() = coroutineScope {
 *         launch { work() }  // Tied to process() lifecycle
 *     }
 * }
 * ```
 *
 * If you intentionally need fire-and-forget, make it explicit and don't use suspend:
 *
 * ```kotlin
 * // ✅ GOOD: Explicit fire-and-forget (non-suspend)
 * class MyService(private val scope: CoroutineScope) {
 *     fun fireAndForget() {
 *         scope.launch { work() }
 *     }
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   ExternalScopeLaunch:
 *     active: true
 * ```
 *
 * ## Note
 *
 * This is a heuristic rule and may have false positives. The rule flags cases where:
 * - A suspend function launches on a scope that is a class property
 * - The scope appears to come from outside the function
 *
 * Framework scopes like viewModelScope, lifecycleScope are excluded.
 */
class ExternalScopeLaunchRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ExternalScopeLaunch",
        severity = Severity.Warning,
        description = "Launching coroutine on external scope from suspend function. " +
            "This breaks structured concurrency. Use coroutineScope { } instead, " +
            "or make the function non-suspend if fire-and-forget is intentional.",
        debt = Debt.TEN_MINS
    )

    /**
     * Framework scopes that are lifecycle-aware and acceptable.
     */
    private val frameworkScopes = setOf(
        "viewModelScope",
        "lifecycleScope",
        "rememberCoroutineScope"
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        // Check if this is launch or async
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName !in setOf("launch", "async")) return

        // Check if we're inside a suspend function
        val containingFunction = expression.getParentOfType<KtNamedFunction>(strict = true) ?: return
        if (!CoroutineDetektUtils.isSuspendFunction(containingFunction)) return

        // Get the scope being used (the receiver of the call)
        val parent = expression.parent
        if (parent !is KtDotQualifiedExpression) return
        
        val receiver = parent.receiverExpression
        val scopeName = when (receiver) {
            is KtNameReferenceExpression -> receiver.getReferencedName()
            else -> return
        }

        // Skip framework scopes
        if (scopeName in frameworkScopes) return

        // Check if the scope is a class property (external scope)
        val containingClass = expression.getParentOfType<KtClass>(strict = true)
        if (containingClass != null && isClassProperty(containingClass, scopeName)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "Launching on external scope '$scopeName' from suspend function " +
                        "'${containingFunction.name}'. This breaks structured concurrency. " +
                        "Use coroutineScope { launch { } } to maintain parent-child relationship, " +
                        "or make the function non-suspend if fire-and-forget is intentional."
                )
            )
        }
    }

    /**
     * Checks if a name refers to a class property.
     */
    private fun isClassProperty(ktClass: KtClass, propertyName: String): Boolean {
        // Check primary constructor parameters
        val primaryConstructor = ktClass.primaryConstructor
        val constructorParams = primaryConstructor?.valueParameters?.map { it.name } ?: emptyList()
        if (propertyName in constructorParams) return true

        // Check class body properties
        val classProperties = ktClass.body?.properties?.map { it.name } ?: emptyList()
        if (propertyName in classProperties) return true

        return false
    }
}
