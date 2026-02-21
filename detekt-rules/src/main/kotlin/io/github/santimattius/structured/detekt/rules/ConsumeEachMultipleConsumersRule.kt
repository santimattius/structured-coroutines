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
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Detekt rule that detects the same Channel used with `consumeEach` from multiple coroutines.
 *
 * ## Problem (Best Practice 7.2 - CHANNEL_002)
 *
 * Using `consumeEach` from multiple coroutines on the same channel cancels the channel
 * when the first consumer finishes, breaking other consumers.
 *
 * ## Recommended
 *
 * For fan-out (multiple consumers), use `for (value in channel)` in each consumer.
 * Reserve `consumeEach` for single-consumer scenarios only.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   ConsumeEachMultipleConsumers:
 *     active: true
 * ```
 *
 * ## Note
 *
 * Heuristic: matches by variable name. Same channel referenced by different variables
 * is not detected; different variables that refer to the same channel at runtime are not
 * reported.
 */
class ConsumeEachMultipleConsumersRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ConsumeEachMultipleConsumers",
        severity = Severity.Warning,
        description = "[CHANNEL_002] Same channel used with consumeEach from multiple coroutines. " +
            "Use for (value in channel) per consumer. " +
            "See: ${DetektDocUrl.buildDocLink("72-sharing-consumeeach-among-multiple-consumers")}",
        debt = Debt.TEN_MINS
    )

    private val builderNames = setOf("launch", "async")

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        val body = function.bodyExpression ?: return
        val block = body as? KtBlockExpression ?: return

        // Only consider launch/async that are direct statements of the function body (sibling builders)
        val builderLambdas = block.statements
            .mapNotNull { expr ->
                val call = when (expr) {
                    is KtCallExpression -> if (expr.calleeExpression?.text in builderNames) expr else null
                    is KtDotQualifiedExpression -> (expr.selectorExpression as? KtCallExpression)
                        ?.takeIf { it.calleeExpression?.text in builderNames }
                    else -> null
                }
                call?.let { getLambdaArgument(it) }
            }
            .toList()

        if (builderLambdas.size < 2) return

        // For each builder lambda, collect channel names used in consumeEach
        val consumeEachByBuilder = builderLambdas.map { lambda ->
            lambda.collectDescendantsOfType<KtDotQualifiedExpression>()
                .filter { dq -> isConsumeEachCall(dq) }
                .mapNotNull { getReceiverName(it) }
                .toSet()
        }

        // Find channel names that appear in more than one builder
        val allConsumeEachReceivers = consumeEachByBuilder.flatten().toList()
        val duplicatedNames = allConsumeEachReceivers
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        if (duplicatedNames.isEmpty()) return

        // Report on each consumeEach that uses a duplicated channel name
        for (lambda in builderLambdas) {
            lambda.collectDescendantsOfType<KtDotQualifiedExpression>()
                .filter { dq -> isConsumeEachCall(dq) }
                .forEach { dq ->
                    val name = getReceiverName(dq) ?: return@forEach
                    if (name in duplicatedNames) {
                        report(
                            CodeSmell(
                                issue = issue,
                                entity = Entity.from(dq),
                                message = "[CHANNEL_002] Channel '$name' is used with consumeEach from multiple coroutines. " +
                                    "Use for (value in $name) in each consumer. " +
                                    "See: ${DetektDocUrl.buildDocLink("72-sharing-consumeeach-among-multiple-consumers")}"
                            )
                        )
                    }
                }
        }
    }

    private fun getLambdaArgument(call: KtCallExpression): KtLambdaExpression? {
        val lambdaArg = call.lambdaArguments.firstOrNull() ?: return null
        return lambdaArg.getArgumentExpression() as? KtLambdaExpression
    }

    private fun isConsumeEachCall(dq: KtDotQualifiedExpression): Boolean {
        val sel = dq.selectorExpression ?: return false
        return (sel as? KtCallExpression)?.calleeExpression?.text == "consumeEach" ||
            sel.text.startsWith("consumeEach")
    }

    private fun getReceiverName(dq: KtDotQualifiedExpression): String? {
        val receiver = dq.receiverExpression
        return when (receiver) {
            is KtNameReferenceExpression -> receiver.getReferencedName()
            else -> receiver.text
        }
    }
}
