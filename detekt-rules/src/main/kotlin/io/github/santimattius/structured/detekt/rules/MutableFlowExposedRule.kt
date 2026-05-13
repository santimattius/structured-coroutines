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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtProperty

/**
 * [FLOW_010] — public `MutableStateFlow` / `MutableSharedFlow` properties leak write APIs.
 *
 * Mirrors the iteration plan heuristic: expose `StateFlow` / `SharedFlow` via `.asStateFlow()`
 * / `.asSharedFlow()` on backing properties.
 */
class MutableFlowExposedRule(config: Config = Config.empty) : Rule(config) {

    override val issue = Issue(
        id = "MutableFlowExposed",
        severity = Severity.CodeSmell,
        description = "[FLOW_010] Exposing MutableStateFlow/MutableSharedFlow is usually an API leak. " +
            "Prefer `private val _x = MutableStateFlow(...)` and `val x = _x.asStateFlow()`. " +
            "See: ${DetektDocUrl.buildDocLink("95-flow_010--mutablestateflow-exposed")}",
        debt = Debt.TEN_MINS
    )

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)
        if (!CoroutinesImportFilter.elementImportsCoroutinesOrFlow(property)) return
        val fileName = property.containingKtFile.name
        val path = property.containingKtFile.virtualFilePath
        if (CoroutineDetektUtils.isTestFile(fileName) || CoroutineDetektUtils.isTestFile(path)) return
        if (KtPsiUtil.isLocal(property)) return
        if (property.hasModifier(KtTokens.PRIVATE_KEYWORD) ||
            property.hasModifier(KtTokens.INTERNAL_KEYWORD) ||
            property.hasModifier(KtTokens.PROTECTED_KEYWORD)
        ) {
            return
        }

        val typeText = property.typeReference?.text.orEmpty()
        val typeShowsMutableFlow =
            typeText.contains("MutableStateFlow") || typeText.contains("MutableSharedFlow")

        val init = property.initializer
        val ctorMutableFlow = init is KtCallExpression &&
            init.calleeExpression?.text?.let { name ->
                name == "MutableStateFlow" ||
                    name == "MutableSharedFlow" ||
                    name.endsWith(".MutableStateFlow") ||
                    name.endsWith(".MutableSharedFlow")
            } == true

        val preview = property.annotationEntries.any {
            val s = it.shortName?.asString().orEmpty()
            s.endsWith("Preview", ignoreCase = true)
        }
        if (preview) return

        if (!(typeShowsMutableFlow || ctorMutableFlow)) return

        report(
            CodeSmell(
                issue = issue,
                entity = Entity.from(property),
                message = "[FLOW_010] Public mutable Flow surface — hide mutability with `.asStateFlow()` / `.asSharedFlow()`. " +
                    "See: ${DetektDocUrl.buildDocLink("95-flow_010--mutablestateflow-exposed")}"
            )
        )
    }
}
