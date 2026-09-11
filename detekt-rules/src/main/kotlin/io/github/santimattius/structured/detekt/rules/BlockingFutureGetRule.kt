/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression

/**
 * [INTEROP_004] — blocking [java.util.concurrent.Future.get] inside coroutines.
 */
class BlockingFutureGetRule(config: Config = Config.empty) : Rule(
    config,
    description = "[INTEROP_004] Use .await() from kotlinx-coroutines-jdk8 or kotlinx-coroutines-guava. " +
            "See: ${DetektDocUrl.buildDocLink("104-interop_004--blocking-future-get")}",
) {

    override val ruleName: RuleName get() = RuleName("BlockingFutureGet")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return
        if (expression.calleeExpression?.text != "get") return

        val parent = expression.parent as? KtDotQualifiedExpression ?: return
        val receiverText = parent.receiverExpression.text
        if (!receiverText.contains("Future", ignoreCase = true)) return

        if (!CoroutineDetektUtils.isInsideCoroutine(expression)) return

        report(
            Finding(
                entity = Entity.from(expression),
                message = "[INTEROP_004] .get() blocks the dispatcher thread — use .await() from " +
                    "kotlinx-coroutines-jdk8 or kotlinx-coroutines-guava. " +
                    "See: ${DetektDocUrl.buildDocLink("104-interop_004--blocking-future-get")}",
            ),
        )
    }
}
