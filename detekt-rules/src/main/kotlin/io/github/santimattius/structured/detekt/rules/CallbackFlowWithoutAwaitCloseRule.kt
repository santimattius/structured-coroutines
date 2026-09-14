/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

/**
 * [INTEROP_002] — `callbackFlow { }` should call `awaitClose { }` for listener cleanup (mirrors FIR rule).
 */
class CallbackFlowWithoutAwaitCloseRule(config: Config = Config.empty) : Rule(
    config,
    description = "[INTEROP_002] 'callbackFlow' block must invoke 'awaitClose' to unregister listeners. " +
            "See: ${DetektDocUrl.buildDocLink("102-interop_002--callbackflow-without-awaitclose")}",
) {

    override val ruleName: RuleName get() = RuleName("CallbackFlowWithoutAwaitClose")

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        if (!CoroutinesImportFilter.elementImportsCoroutinesOrFlow(expression)) return

        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "callbackFlow") return

        val lambdaExpr = extractTrailingLambda(expression) ?: return
        if (lambdaBodyContainsAwaitClose(lambdaExpr)) return

        report(
            Finding(
                entity = Entity.from(expression),
                message = "[INTEROP_002] 'callbackFlow' without 'awaitClose' — add 'awaitClose { /* unregister */ }' " +
                    "so collectors can cancel cleanly. See: ${DetektDocUrl.buildDocLink("102-interop_002--callbackflow-without-awaitclose")}"
            )
        )
    }

    private fun extractTrailingLambda(expression: KtCallExpression): KtLambdaExpression? {
        expression.lambdaArguments.lastOrNull()?.getArgumentExpression()?.let { arg ->
            if (arg is KtLambdaExpression) return arg
        }
        return null
    }

    private fun lambdaBodyContainsAwaitClose(lambda: KtLambdaExpression): Boolean =
        lambda.bodyExpression?.anyDescendantOfType<KtCallExpression> { call ->
            call.calleeExpression?.text == "awaitClose"
        } ?: false
}
