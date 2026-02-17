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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Detekt rule that detects runBlocking calls inside suspend functions.
 *
 * ## Problem (Best Practice 2.2)
 *
 * Calling runBlocking inside a suspend function blocks the thread and defeats the purpose
 * of coroutines. runBlocking is meant to be used only at the top level (main functions,
 * test functions) to bridge blocking and non-blocking code.
 *
 * ```kotlin
 * // ❌ BAD: runBlocking in suspend function
 * suspend fun fetchData() {
 *     runBlocking {  // Blocks the thread!
 *         delay(1000)
 *         loadFromNetwork()
 *     }
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Just use suspend functions directly:
 *
 * ```kotlin
 * // ✅ GOOD: Direct suspend
 * suspend fun fetchData() {
 *     delay(1000)  // Non-blocking
 *     loadFromNetwork()
 * }
 *
 * // ✅ GOOD: runBlocking at top level (entry point)
 * fun main() = runBlocking {
 *     fetchData()
 * }
 *
 * @Test
 * fun testSomething() = runBlocking {
 *     val result = fetchData()
 *     assertEquals(expected, result)
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   RunBlockingInSuspend:
 *     active: true
 *     severity: warning
 * ```
 */
class RunBlockingInSuspendRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "RunBlockingInSuspend",
        severity = Severity.CodeSmell,
        description = "[RUNBLOCK_002] runBlocking should not be called inside suspend functions. " +
            "It blocks the thread and defeats the purpose of coroutines. " +
            "Use runBlocking only at top level (main, test functions). " +
            "See: ${DetektDocUrl.buildDocLink("22-runblock_002--using-runblocking-inside-suspend-functions")}",
        debt = Debt.TEN_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "runBlocking") return

        // Check if we're inside a suspend function
        val parentFunction = expression.getParentOfType<KtNamedFunction>(strict = true)
        if (parentFunction?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "[RUNBLOCK_002] runBlocking blocks the thread and defeats coroutines purpose. " +
                        "Remove runBlocking and use suspend functions directly. " +
                        "runBlocking should only be used at top level (main, test functions). " +
                        "See: ${DetektDocUrl.buildDocLink("22-runblock_002--using-runblocking-inside-suspend-functions")}"
                )
            )
        }
    }
}
