/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.utils

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtTryExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

/**
 * Shared [FLOW_005] logic for Detekt. Keep in sync with
 * `lint-rules/.../MissingCatchInFlowHeuristic.kt` (Android Lint).
 */
object MissingCatchInFlowHeuristic {

    private val FLOW_TERMINALS = setOf("collect", "collectLatest", "launchIn")

    private val FLOW_INTERMEDIATES = setOf(
        "map",
        "mapLatest",
        "filter",
        "filterNot",
        "transform",
        "flatMapConcat",
        "flatMapLatest",
        "flatMapMerge",
        "distinctUntilChanged",
        "debounce",
        "sample",
        "take",
        "drop",
        "onStart",
        "onEmpty",
        "onCompletion",
        "retry",
        "retryWhen",
        "runningFold",
        "runningReduce",
    )

    fun shouldReportTerminalCall(terminalFlowCall: KtCallExpression): Boolean {
        val callee = terminalFlowCall.calleeExpression?.text ?: return false
        if (callee !in FLOW_TERMINALS) return false

        val receiverChainCalls = callersInReceiverChainBeforeTerminal(terminalFlowCall)

        if (receiverChainCalls.any { it.calleeExpression?.text == "catch" }) return false

        val hasTrackedIntermediate = receiverChainCalls.any {
            val n = it.calleeExpression?.text ?: return@any false
            n in FLOW_INTERMEDIATES
        }
        if (!hasTrackedIntermediate) return false

        if (isInsideCatchAllThrowableOrException(terminalFlowCall)) return false

        return true
    }

    fun callersInReceiverChainBeforeTerminal(terminalFlowCall: KtCallExpression): List<KtCallExpression> {
        val dot = terminalFlowCall.parent as? KtDotQualifiedExpression ?: return emptyList()
        if (dot.selectorExpression != terminalFlowCall) return emptyList()
        return extractQualifiedChainCalls(dot.receiverExpression)
    }

    private fun extractQualifiedChainCalls(receiver: KtExpression?): List<KtCallExpression> {
        if (receiver == null) return emptyList()
        return when (receiver) {
            is KtDotQualifiedExpression -> {
                val selector = receiver.selectorExpression
                val suffix = if (selector is KtCallExpression) listOf(selector) else emptyList()
                extractQualifiedChainCalls(receiver.receiverExpression) + suffix
            }
            is KtCallExpression -> listOf(receiver)
            else -> emptyList()
        }
    }

    private fun isInsideCatchAllThrowableOrException(element: KtElement): Boolean {
        var tryExpr: KtTryExpression? =
            element.getParentOfType<KtTryExpression>(strict = false) ?: return false

        while (tryExpr != null) {
            val inCatch = tryExpr.catchClauses.any { clause ->
                val body = clause.catchBody
                body != null && body.isAncestor(element, false)
            }
            if (inCatch) {
                tryExpr =
                    (tryExpr.parent as? KtElement)?.getParentOfType<KtTryExpression>(strict = false)
                continue
            }

            val tryBlock = tryExpr.tryBlock
            if (tryBlock != null && tryBlock.isAncestor(element, false) &&
                catchesThrowableOrGenericException(tryExpr)
            ) {
                return true
            }

            tryExpr =
                (tryExpr.parent as? KtElement)?.getParentOfType<KtTryExpression>(strict = false)
        }
        return false
    }

    private fun catchesThrowableOrGenericException(tryExpr: KtTryExpression): Boolean =
        tryExpr.catchClauses.any { clause ->
            val names = clause.catchParameter?.typeReference?.text ?: return@any false
            val normalized = names.trim()
            normalized == "Throwable" ||
                normalized == "java.lang.Throwable" ||
                normalized.endsWith(".Throwable") ||
                normalized == "Exception" ||
                normalized == "java.lang.Exception" ||
                normalized.endsWith(".Exception")
        }
}
