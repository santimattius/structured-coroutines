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
import org.jetbrains.kotlin.psi.KtNamedFunction

/**
 * [TEST_004] — JUnit tests that use `runBlocking` as the test body should prefer `runTest` from
 * kotlinx-coroutines-test (virtual time, structured test scope).
 *
 * Complements [RunBlockingWithDelayInTestRule] ([TEST_001]) which flags real `delay` under `runBlocking`.
 */
class RunBlockingInsteadOfRunTestRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "RunBlockingInsteadOfRunTest",
        severity = Severity.Warning,
        description = "[TEST_004] Prefer `runTest { }` over `runBlocking { }` in `@Test` functions for coroutine tests. " +
            "See: ${DetektDocUrl.buildDocLink("64-test_004--runblocking-instead-of-runtest")}",
        debt = Debt.FIVE_MINS
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!function.hasAnnotationNamedTest()) return
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(function)) return

        val fileName = function.containingKtFile.name
        val path = function.containingKtFile.virtualFilePath
        if (!CoroutineDetektUtils.isTestFile(fileName) && !CoroutineDetektUtils.isTestFile(path)) return

        val bodyExpr = function.bodyExpression as? KtCallExpression ?: return
        if (bodyExpr.calleeExpression?.text != "runBlocking") return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(bodyExpr),
                message = "[TEST_004] Replace `runBlocking` with `runTest` from kotlinx-coroutines-test for faster, " +
                    "deterministic coroutine tests. See: ${DetektDocUrl.buildDocLink("64-test_004--runblocking-instead-of-runtest")}"
            )
        )
    }

    private fun KtNamedFunction.hasAnnotationNamedTest(): Boolean =
        annotationEntries.any { it.shortName?.asString() == "Test" }
}
