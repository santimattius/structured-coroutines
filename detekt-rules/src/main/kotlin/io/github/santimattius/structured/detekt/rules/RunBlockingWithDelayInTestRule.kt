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
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Detekt rule that detects runBlocking with delay in test files.
 *
 * ## Problem (Best Practice 6.1)
 *
 * Using runBlocking with delay() in tests makes tests slow and flaky because
 * they wait for real time to pass. This defeats the purpose of virtual time
 * testing provided by kotlinx-coroutines-test.
 *
 * ```kotlin
 * // ❌ BAD: Slow test with real delays
 * @Test
 * fun `test something`() = runBlocking {
 *     delay(1000)  // Waits 1 real second
 *     val result = repository.getData()
 *     assertEquals(expected, result)
 * }
 * ```
 *
 * ## Recommended Practice
 *
 * Use runTest from kotlinx-coroutines-test which provides virtual time:
 *
 * ```kotlin
 * // ✅ GOOD: Fast test with virtual time
 * @Test
 * fun `test something`() = runTest {
 *     delay(1000)  // Instant - uses virtual time
 *     val result = repository.getData()
 *     assertEquals(expected, result)
 * }
 * ```
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   RunBlockingWithDelayInTest:
 *     active: true
 * ```
 *
 * ## Note
 *
 * This rule only applies to files that match test naming conventions:
 * - *Test.kt
 * - *Tests.kt
 * - *Spec.kt
 * - Files in /test/ or /androidTest/ directories
 */
class RunBlockingWithDelayInTestRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "RunBlockingWithDelayInTest",
        severity = Severity.Warning,
        description = "Using runBlocking with delay() in tests makes tests slow. " +
            "Use runTest { } from kotlinx-coroutines-test for virtual time support.",
        debt = Debt.FIVE_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)

        // Only check in test files
        val fileName = expression.containingKtFile.name
        val filePath = expression.containingKtFile.virtualFilePath
        if (!CoroutineDetektUtils.isTestFile(fileName) && !CoroutineDetektUtils.isTestFile(filePath)) {
            return
        }

        // Check if this is a runBlocking call
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "runBlocking") return

        // Check if the lambda contains delay()
        val lambdaArg = expression.lambdaArguments.firstOrNull() ?: return
        if (containsDelayCall(lambdaArg)) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "runBlocking with delay() in test file. " +
                        "Use runTest { } from kotlinx-coroutines-test for instant virtual time. " +
                        "Example: @Test fun myTest() = runTest { delay(1000) /* instant */ }"
                )
            )
        }
    }

    /**
     * Checks if the lambda argument contains any delay() calls.
     */
    private fun containsDelayCall(lambdaArg: KtLambdaArgument): Boolean {
        val callExpressions = lambdaArg.collectDescendantsOfType<KtCallExpression>()
        return callExpressions.any { call ->
            call.calleeExpression?.text == "delay"
        }
    }
}
