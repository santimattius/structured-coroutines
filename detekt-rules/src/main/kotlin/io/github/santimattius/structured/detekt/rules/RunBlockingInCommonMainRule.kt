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

/**
 * [KMP_002] — `runBlocking` in commonMain/commonTest is invalid on JS and risky on Native main thread.
 */
class RunBlockingInCommonMainRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "RunBlockingInCommonMain",
        severity = Severity.Defect,
        description = "[KMP_002] runBlocking in common Kotlin source is not portable to JS/Native. " +
            "Use suspend APIs or platform-specific entry points. " +
            "See: ${DetektDocUrl.buildDocLink("112-kmp_002--runblockingincommonmain")}",
        debt = Debt.FIVE_MINS,
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        if (expression.calleeExpression?.text != "runBlocking") return

        val path = expression.containingKtFile.virtualFilePath
        if (!CoroutineDetektUtils.isKotlinCommonLikeSourceVirtualPath(path)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[KMP_002] runBlocking in common Kotlin source — not supported on JS; can deadlock " +
                    "on iOS main thread. Use suspend functions or expect/actual bridges. " +
                    "See: ${DetektDocUrl.buildDocLink("112-kmp_002--runblockingincommonmain")}",
            ),
        )
    }
}
