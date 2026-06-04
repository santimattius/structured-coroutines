/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.utils

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement

/**
 * [INF-001] MVP — analyzes a Flow operator chain from a dot-qualified expression upward.
 */
object FlowChainAnalyzer {

    data class FlowChainFinding(val symbol: String, val message: String)

    private val flowOperators = setOf(
        "map", "filter", "flatMapLatest", "flatMapMerge", "flatMapConcat",
        "onEach", "catch", "distinctUntilChanged", "flowOn", "collect",
        "collectLatest", "launchIn", "transform", "debounce",
    )

    fun findFlowDotChain(element: KtElement): KtDotQualifiedExpression? {
        var current: KtElement? = element
        while (current != null) {
            if (current is KtDotQualifiedExpression && chainContainsFlowOperator(current)) {
                return current
            }
            current = current.parent as? KtElement
        }
        return null
    }

    fun analyze(chainRoot: KtDotQualifiedExpression): List<FlowChainFinding> {
        val operators = collectOperators(chainRoot)
        val findings = mutableListOf<FlowChainFinding>()

        if ("catch" in operators) {
            findings += FlowChainFinding("✅", "catch — error handling present")
        } else {
            findings += FlowChainFinding("⚠️", "missing catch — upstream errors propagate to the collector scope")
        }

        if ("onEach" in operators) {
            findings += FlowChainFinding("✅", "onEach — side effects separated from map")
        }

        if ("distinctUntilChanged" !in operators) {
            findings += FlowChainFinding("⚠️", "missing distinctUntilChanged — potential redundant emissions")
        }

        when {
            "flatMapLatest" in operators ->
                findings += FlowChainFinding("ℹ️", "flatMapLatest — last-wins semantics (suitable for search, not for downloads)")
            "flatMapConcat" in operators ->
                findings += FlowChainFinding("ℹ️", "flatMapConcat — serializes inner flows (suitable for ordered work)")
            "flatMapMerge" in operators ->
                findings += FlowChainFinding("ℹ️", "flatMapMerge — concurrent inner flows (watch dispatcher pressure)")
        }

        if ("flowOn" !in operators) {
            findings += FlowChainFinding("→", "Dispatcher: inherited (not pinned with flowOn)")
        } else {
            findings += FlowChainFinding("→", "Dispatcher: flowOn present in chain")
        }

        return findings
    }

    fun formatReport(findings: List<FlowChainFinding>): String {
        val header = "Flow chain analysis:"
        val body = findings.joinToString("\n") { "  ${it.symbol} ${it.message}" }
        return "$header\n$body"
    }

    private fun chainContainsFlowOperator(dotExpr: KtDotQualifiedExpression): Boolean =
        collectOperators(dotExpr).isNotEmpty()

    private fun collectOperators(dotExpr: KtDotQualifiedExpression): Set<String> {
        val names = mutableSetOf<String>()
        var receiver: org.jetbrains.kotlin.psi.KtExpression? = dotExpr.receiverExpression
        val terminal = dotExpr.selectorExpression as? KtCallExpression
        terminal?.calleeExpression?.text?.let { names += it }

        while (receiver is KtDotQualifiedExpression) {
            val selectorCall = receiver.selectorExpression as? KtCallExpression
            selectorCall?.calleeExpression?.text?.let { name ->
                if (name in flowOperators) names += name
            }
            receiver = receiver.receiverExpression
        }
        val receiverCall = receiver as? KtCallExpression
        receiverCall?.calleeExpression?.text?.let { name ->
            if (name in flowOperators) names += name
        }
        return names
    }
}
