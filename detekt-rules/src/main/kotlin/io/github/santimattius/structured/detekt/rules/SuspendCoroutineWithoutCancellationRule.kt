/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * [INTEROP_001] — mirrors the compiler rule: `suspendCoroutine` does not support cancellation.
 */
class SuspendCoroutineWithoutCancellationRule(config: Config = Config.empty) : Rule(
    config,
    description = "[INTEROP_001] 'suspendCoroutine' does not support cancellation. " +
            "Use 'suspendCancellableCoroutine' and 'invokeOnCancellation'. " +
            "See: ${DetektDocUrl.buildDocLink("101-interop_001--wrapping-callbacks-without-cancellation-support")}",
) {

    override val ruleName: RuleName get() = RuleName("SuspendCoroutineWithoutCancellation")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return

        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "suspendCoroutine") return

        val fn = CoroutineDetektUtils.enclosingNamedFunction(expression) ?: return
        if (!CoroutineDetektUtils.isSuspendFunction(fn)) return

        report(
            Finding(
                entity = Entity.from(expression),
                message = "[INTEROP_001] 'suspendCoroutine' does not propagate cancellation from the caller. " +
                    "Prefer 'suspendCancellableCoroutine' + 'invokeOnCancellation { }' for callback cleanup. " +
                    "See: ${DetektDocUrl.buildDocLink("101-interop_001--wrapping-callbacks-without-cancellation-support")}"
            )
        )
    }
}
