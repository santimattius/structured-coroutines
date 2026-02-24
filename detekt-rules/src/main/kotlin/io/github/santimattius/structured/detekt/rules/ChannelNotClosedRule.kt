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
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Detekt rule that detects manual `Channel()` creation without a corresponding `close()` call.
 *
 * ## Problem (Best Practice 7.1 - CHANNEL_001)
 *
 * Creating a `Channel` manually and never calling `close()` can cause consumers using
 * `for (x in channel)` to block forever.
 *
 * ## Recommended
 *
 * Use `produce { }` which closes the channel when the coroutine terminates.
 * If managing channels manually, define clearly when and where they are closed.
 *
 * ## Configuration
 *
 * ```yaml
 * structured-coroutines:
 *   ChannelNotClosed:
 *     active: true
 * ```
 *
 * ## Note
 *
 * Heuristic: only checks within the same function. Channels closed in another function
 * or via structured concurrency may still be reported. Suppress when appropriate.
 */
class ChannelNotClosedRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ChannelNotClosed",
        severity = Severity.Warning,
        description = "[CHANNEL_001] Manual Channel created without close(). " +
            "Use produce { } or ensure close() is called. " +
            "See: ${DetektDocUrl.buildDocLink("71-forgetting-to-close-manual-channels")}",
        debt = Debt.TEN_MINS
    )

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val calleeName = expression.calleeExpression?.text ?: return
        if (calleeName != "Channel" && !calleeName.startsWith("Channel<")) return

        // Do not report when Channel is created inside produce { } (produce closes automatically)
        if (isInsideProduce(expression)) return

        val variableName = getAssignedVariableName(expression) ?: return
        val function = expression.getParentOfType<KtNamedFunction>(strict = false) ?: return
        val body = function.bodyExpression ?: return

        val hasClose = body.collectDescendantsOfType<KtDotQualifiedExpression>().any { dq ->
            isCloseCall(dq) && getReceiverName(dq) == variableName
        }
        if (!hasClose) {
            report(
                CodeSmell(
                    issue = issue,
                    entity = Entity.from(expression),
                    message = "[CHANNEL_001] Channel '$variableName' may never be closed. " +
                        "Use produce { } or call $variableName.close(). " +
                        "See: ${DetektDocUrl.buildDocLink("71-forgetting-to-close-manual-channels")}"
                )
            )
        }
    }

    private fun isInsideProduce(expression: KtCallExpression): Boolean {
        var current: org.jetbrains.kotlin.psi.KtElement? = expression.parent as? org.jetbrains.kotlin.psi.KtElement
        while (current != null) {
            val lambdaArg = current.getParentOfType<KtLambdaArgument>(strict = true) ?: run {
                current = current.parent as? org.jetbrains.kotlin.psi.KtElement
                continue
            }
            val call = lambdaArg.getParentOfType<KtCallExpression>(strict = true) ?: run {
                current = current.parent as? org.jetbrains.kotlin.psi.KtElement
                continue
            }
            if (call.calleeExpression?.text == "produce") return true
            current = current.parent as? org.jetbrains.kotlin.psi.KtElement
        }
        return false
    }

    private fun getAssignedVariableName(channelCall: KtCallExpression): String? {
        val prop = channelCall.getParentOfType<KtProperty>(strict = false) ?: run {
            val binary = channelCall.getParentOfType<KtBinaryExpression>(strict = false) ?: return null
            val left = binary.left ?: return null
            return (left as? KtNameReferenceExpression)?.getReferencedName()
        }
        val initializer = prop.initializer ?: return null
        var p: org.jetbrains.kotlin.psi.KtElement? = channelCall.parent as? org.jetbrains.kotlin.psi.KtElement
        while (p != null && p != prop) {
            if (p == initializer) return prop.name
            p = p.parent as? org.jetbrains.kotlin.psi.KtElement
        }
        return if (initializer == channelCall) prop.name else null
    }

    private fun isCloseCall(dq: KtDotQualifiedExpression): Boolean {
        val sel = dq.selectorExpression ?: return false
        return (sel as? KtCallExpression)?.calleeExpression?.text == "close" ||
            sel.text.startsWith("close")
    }

    private fun getReceiverName(dq: KtDotQualifiedExpression): String? {
        val receiver = dq.receiverExpression
        return when (receiver) {
            is KtNameReferenceExpression -> receiver.getReferencedName()
            else -> receiver.text
        }
    }
}
