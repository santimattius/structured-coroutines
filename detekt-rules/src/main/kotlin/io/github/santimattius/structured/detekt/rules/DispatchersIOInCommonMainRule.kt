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
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
/**
 * [KMP_001] — `Dispatchers.IO` cannot be referenced from Kotlin/Common source sets targeting Native/JS.
 */
class DispatchersIOInCommonMainRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "DispatchersIOInCommonMain",
        severity = Severity.Defect,
        description = "[KMP_001] `Dispatchers.IO` is JVM/Android-only — inject `CoroutineDispatcher` or use expect/actual. " +
            "See: ${DetektDocUrl.buildDocLink("111-kmp_001--dispatchersio-in-commonmain")}",
        debt = Debt.FIVE_MINS
    )

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return

        val path = expression.containingKtFile.virtualFilePath
        if (!CoroutineDetektUtils.isKotlinCommonLikeSourceVirtualPath(path)) return

        val receiverText = expression.receiverExpression.text.trim()

        val targetsIo =
            receiverText.endsWith("Dispatchers") ||
                receiverText == "Dispatchers" ||
                receiverText.endsWith(".Dispatchers")

        val selector = expression.selectorExpression?.text ?: return
        if (!targetsIo || selector != "IO") return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[KMP_001] `Dispatchers.IO` in common Kotlin source — use dispatcher injection or " +
                    "expect/actual to supply a platform-backed I/O dispatcher. " +
                    "See: ${DetektDocUrl.buildDocLink("111-kmp_001--dispatchersio-in-commonmain")}"
            )
        )
    }

}
