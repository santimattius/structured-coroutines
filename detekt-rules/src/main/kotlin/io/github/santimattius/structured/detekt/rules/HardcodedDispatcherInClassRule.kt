/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

/**
 * [TEST_005] — hardcoded [Dispatchers.IO] / [Dispatchers.Main] in production classes.
 */
class HardcodedDispatcherInClassRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "HardcodedDispatcherInClass",
        severity = Severity.Warning,
        description = "[TEST_005] Inject CoroutineDispatcher instead of hardcoding Dispatchers.IO/Main. " +
            "See: ${DetektDocUrl.buildDocLink("65-test_005--hardcoded-dispatcher-in-class")}",
        debt = Debt.FIVE_MINS,
    )

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
        super.visitDotQualifiedExpression(expression)
        if (!CoroutinesImportFilter.elementIsInCoroutinesFile(expression)) return

        val file = expression.containingKtFile
        val path = file.virtualFilePath
        val name = file.name
        if (CoroutineDetektUtils.isTestFile(path) || CoroutineDetektUtils.isTestFile(name)) return

        if (isInDefaultParameterValue(expression)) return

        val receiverText = expression.receiverExpression.text.trim()
        if (!receiverText.endsWith("Dispatchers") && receiverText != "Dispatchers") return

        val selector = expression.selectorExpression?.text ?: return
        if (selector != "IO" && selector != "Main") return

        if (!CoroutineDetektUtils.isInsideCoroutine(expression)) return

        val klass = expression.getParentOfType<KtClass>(strict = true) ?: return
        if (klass.hasDispatcherQualifierParameter()) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(expression),
                message = "[TEST_005] Inject CoroutineDispatcher (@IoDispatcher / @MainDispatcher) instead of " +
                    "hardcoding Dispatchers.$selector; use UnconfinedTestDispatcher or StandardTestDispatcher in tests. " +
                    "See: ${DetektDocUrl.buildDocLink("65-test_005--hardcoded-dispatcher-in-class")}",
            ),
        )
    }

    private fun isInDefaultParameterValue(element: KtElement): Boolean {
        val parameter = element.getParentOfType<KtParameter>(strict = true) ?: return false
        val default = parameter.defaultValue ?: return false
        return default.isAncestor(element)
    }

    private fun KtClass.hasDispatcherQualifierParameter(): Boolean {
        val params = primaryConstructor?.valueParameters.orEmpty() +
            secondaryConstructors.flatMap { it.valueParameters }
        return params.any { param ->
            param.annotationEntries.any { ann ->
                val short = ann.shortName?.asString()
                short == "IoDispatcher" || short == "MainDispatcher"
            }
        }
    }
}
