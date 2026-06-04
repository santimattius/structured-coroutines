/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType

/** [INTEROP_003] — choose [callbackFlow] vs [channelFlow] correctly. */
class ChannelFlowVsCallbackFlowInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.channelflow.vs.callbackflow.display.name"
    override val descriptionKey = "inspection.channelflow.vs.callbackflow.description"

    private val externalCallbackCalleePattern = Regex(
        """(?i)(register|unregister|addListener|removeListener|subscribe|unsubscribe|setCallback|observe|enqueue)""",
    )

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return

                val calleeName = expression.calleeExpression?.text ?: return
                val lambdaExpr = extractTrailingLambda(expression) ?: return

                when (calleeName) {
                    "channelFlow" -> {
                        if (lambdaBodyContainsAwaitClose(lambdaExpr)) return
                        register(
                            holder,
                            expression,
                            "error.interop.channelflow.external.callback",
                        )
                    }
                    "callbackFlow" -> {
                        if (!lambdaBodyContainsAwaitClose(lambdaExpr)) return
                        if (lambdaHasExternalCallbackRegistration(lambdaExpr)) return
                        register(
                            holder,
                            expression,
                            "error.interop.callbackflow.internal.emission",
                        )
                    }
                }
            }
        }

    private fun register(holder: ProblemsHolder, expression: KtCallExpression, messageKey: String) {
        holder.registerProblem(
            expression,
            StructuredCoroutinesBundle.message(messageKey),
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

    private fun lambdaHasExternalCallbackRegistration(lambda: KtLambdaExpression): Boolean =
        lambda.bodyExpression?.anyDescendantOfType<KtCallExpression> { call ->
            externalCallbackCalleePattern.containsMatchIn(call.calleeExpression?.text.orEmpty())
        } ?: false
}
