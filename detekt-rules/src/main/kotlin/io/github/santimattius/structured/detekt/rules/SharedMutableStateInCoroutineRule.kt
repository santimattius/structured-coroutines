/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import io.github.santimattius.structured.detekt.utils.SharedMutableStateHeuristic
import io.gitlab.arturbosch.detekt.api.CodeSmell
import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.api.Debt
import io.gitlab.arturbosch.detekt.api.Entity
import io.gitlab.arturbosch.detekt.api.Issue
import io.gitlab.arturbosch.detekt.api.Rule
import io.gitlab.arturbosch.detekt.api.Severity
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

/**
 * [CONCUR_002] — shared mutable state accessed from multiple launch blocks (default info).
 */
class SharedMutableStateInCoroutineRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "SharedMutableStateInCoroutine",
        severity = Severity.Minor,
        description = "[CONCUR_002] Shared mutable state accessed from parallel launch blocks may race. " +
            "Prefer async/awaitAll or channels. " +
            "See: ${DetektDocUrl.buildDocLink("122-concur_002--sharedmutablestateincoroutine")}",
        debt = Debt.TWENTY_MINS,
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(function)) return
        blocksToAnalyze(function).forEach { reportIssues(it, function) }
    }

    private fun textLooksLikeSharedMutableRace(text: String): Boolean {
        if (!text.contains("mutableListOf") && !text.contains("mutableMapOf") && !text.contains("arrayListOf")) {
            return false
        }
        return Regex("""\blaunch\s*\{""").findAll(text).count() >= 2
    }

    private fun blocksToAnalyze(function: KtNamedFunction): List<KtBlockExpression> {
        val blocks = mutableListOf<KtBlockExpression>()
        val body = function.bodyBlockExpression
        if (body != null) {
            blocks.add(body)
            blocks.addAll(body.coroutineScopeBlocks())
            return blocks.distinct()
        }
        val expr = function.bodyExpression as? KtCallExpression ?: return emptyList()
        if (expr.calleeExpression?.text !in setOf("coroutineScope", "supervisorScope")) return emptyList()
        val inner = expr.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression
            ?: expr.valueArguments.lastOrNull()?.getArgumentExpression()
        if (inner is KtBlockExpression) {
            blocks.add(inner)
        } else {
            expr.getStrictParentOfType<KtNamedFunction>()?.bodyBlockExpression?.let { blocks.add(it) }
        }
        return blocks
    }

    private fun KtBlockExpression.coroutineScopeBlocks(): List<KtBlockExpression> =
        statements.filterIsInstance<KtCallExpression>()
            .filter { it.calleeExpression?.text in setOf("coroutineScope", "supervisorScope") }
            .mapNotNull { call ->
                call.lambdaArguments.firstOrNull()?.getLambdaExpression()?.bodyExpression as? KtBlockExpression
            }

    private fun reportIssues(block: KtBlockExpression, function: KtNamedFunction) {
        val refs = SharedMutableStateHeuristic.findSharedMutableAccessIssues(block)
        if (refs.isEmpty() && textLooksLikeSharedMutableRace(function.text)) {
            reportFunctionIssue(function)
            return
        }
        refs.forEach { ref -> reportReferenceIssue(ref) }
    }

    private fun reportFunctionIssue(function: KtNamedFunction) {
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(function),
                message = "[CONCUR_002] Shared mutable state accessed from multiple launch blocks. " +
                    "Use async/awaitAll or a Channel. " +
                    "See: ${DetektDocUrl.buildDocLink("122-concur_002--sharedmutablestateincoroutine")}",
            ),
        )
    }

    private fun reportReferenceIssue(ref: org.jetbrains.kotlin.psi.KtNameReferenceExpression) {
        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(ref),
                message = "[CONCUR_002] Shared mutable state '${ref.text}' accessed from multiple launch blocks. " +
                    "Use async/awaitAll or a Channel. " +
                    "See: ${DetektDocUrl.buildDocLink("122-concur_002--sharedmutablestateincoroutine")}",
            ),
        )
    }
}
