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
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * [BACKEND_002] — MDC usage with withContext without MDCContext (active when slf4j MDC is imported).
 */
class ThreadLocalNotPropagatedRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "ThreadLocalNotPropagated",
        severity = Severity.Warning,
        description = "[BACKEND_002] MDC not propagated across withContext — add MDCContext(). " +
            "See: ${DetektDocUrl.buildDocLink("37-backend_002--threadlocalnotpropagated")}",
        debt = Debt.TEN_MINS,
    )

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(function)) return
        if (!CoroutineDetektUtils.isSuspendFunction(function)) return
        val file = function.containingKtFile
        if (!fileImportsMdc(file)) return

        val body = function.bodyExpression ?: return
        if (!body.text.contains("MDC.")) return

        body.collectDescendantsOfType<KtCallExpression>()
            .filter { it.calleeExpression?.text == "withContext" }
            .forEach { expression ->
                val ctxText = expression.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: ""
                val usesIoOrDefault = ctxText.contains("Dispatchers.IO") ||
                    ctxText.contains("Dispatchers.Default") ||
                    ctxText.contains("ioDispatcher", ignoreCase = true)
                val hasMdcContext = ctxText.contains("MDCContext")
                if (usesIoOrDefault && !hasMdcContext) {
                    report(
                        CodeSmell(
                            issue = issue,
                            entity = Entity.from(expression),
                            message = "[BACKEND_002] withContext without MDCContext() — MDC will not propagate. " +
                                "Use withContext(Dispatchers.IO + MDCContext()) { }. " +
                                "See: ${DetektDocUrl.buildDocLink("37-backend_002--threadlocalnotpropagated")}",
                        ),
                    )
                }
            }
    }

    private fun fileImportsMdc(file: KtFile): Boolean {
        return file.importDirectives.any { it.importPath?.pathStr?.contains("org.slf4j.MDC") == true } ||
            file.text.contains("org.slf4j.MDC")
    }
}
