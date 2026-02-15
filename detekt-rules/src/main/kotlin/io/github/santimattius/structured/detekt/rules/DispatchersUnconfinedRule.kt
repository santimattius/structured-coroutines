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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Detekt rule that detects Dispatchers.Unconfined usage.
 *
 * ## Problem (Best Practice 3.2)
 *
 * Dispatchers.Unconfined has unpredictable execution thread behavior and should generally
 * be avoided in production code. It's mainly useful for testing or very specific use cases.
 *
 * ```kotlin
 * // ⚠️ WARNING: Dispatchers.Unconfined
 * scope.launch(Dispatchers.Unconfined) {
 *     doWork()  // Unpredictable which thread executes this
 * }
 *
 * withContext(Dispatchers.Unconfined) {
 *     processData()  // May execute on any thread
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Use appropriate dispatchers for your use case:
 *
 * ```kotlin
 * // ✅ GOOD: Use appropriate dispatchers
 * scope.launch(Dispatchers.Default) {  // CPU-bound work
 *     heavyComputation()
 * }
 *
 * scope.launch(Dispatchers.IO) {  // IO-bound work
 *     networkCall()
 *     fileOperation()
 * }
 *
 * scope.launch(Dispatchers.Main) {  // UI updates
 *     updateUI()
 * }
 * ```
 *
 * ## When Dispatchers.Unconfined is Acceptable
 *
 * - Testing scenarios
 * - Very specific performance-critical code where you understand the implications
 * - Legacy code migration (temporary)
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   DispatchersUnconfined:
 *     active: true
 *     severity: warning
 * ```
 */
class DispatchersUnconfinedRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "DispatchersUnconfined",
        severity = Severity.CodeSmell,
        description = "[DISPATCH_003] Dispatchers.Unconfined has unpredictable execution thread behavior. " +
            "Consider using Dispatchers.Default, Dispatchers.IO, or Dispatchers.Main instead. " +
            "See: ${DetektDocUrl.buildDocLink("33-dispatch_003--abusing-dispatchersunconfined")}",
        debt = Debt.TEN_MINS
    )

    private val contextUsingFunctions = setOf("launch", "async", "withContext")

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)

        val receiver = expression.receiverExpression.text
        val selector = expression.selectorExpression?.text

        // Check if it's Dispatchers.Unconfined
        if (receiver == "Dispatchers" && selector == "Unconfined") {
            // Check if it's used in a coroutine builder context
            val parentCall = expression.getParentOfType<KtCallExpression>(strict = true)
            if (parentCall != null) {
                val builderName = parentCall.calleeExpression?.text
                if (builderName in contextUsingFunctions) {
                    report(
                        CodeSmell(
                            issue = issue,
                            entity = Entity.from(expression),
                            message = buildMessage(builderName ?: "coroutine builder")
                        )
                    )
                }
            }
        }
    }

    private fun buildMessage(builderName: String): String {
        return "[DISPATCH_003] Dispatchers.Unconfined in $builderName has unpredictable execution thread. " +
            "Consider using Dispatchers.Default (CPU-bound), Dispatchers.IO (IO-bound), " +
            "or Dispatchers.Main (UI updates) instead. " +
            "See: ${DetektDocUrl.buildDocLink("33-dispatch_003--abusing-dispatchersunconfined")}"
    }
}
