/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.detekt.rules

import io.github.santimattius.structured.detekt.utils.CoroutineDetektUtils
import io.github.santimattius.structured.detekt.utils.CoroutinesImportFilter
import io.github.santimattius.structured.detekt.utils.DetektDocUrl
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import dev.detekt.api.RuleName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

/**
 * [TEST_005] — hardcoded [Dispatchers.IO] / [Dispatchers.Main] in production classes.
 */
class HardcodedDispatcherInClassRule(config: Config = Config.empty) : Rule(
    config,
    description = "[TEST_005] Inject CoroutineDispatcher instead of hardcoding Dispatchers.IO/Main. " +
            "See: ${DetektDocUrl.buildDocLink("65-test_005--hardcoded-dispatcher-in-class")}",
) {

    override val ruleName: RuleName get() = RuleName("HardcodedDispatcherInClass")

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
            Finding(
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
