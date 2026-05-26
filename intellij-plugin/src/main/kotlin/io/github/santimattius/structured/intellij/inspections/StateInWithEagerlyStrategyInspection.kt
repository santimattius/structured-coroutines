/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceEagerlyWithWhileSubscribedQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** [FLOW_006] — stateIn with SharingStarted.Eagerly on lifecycle scopes. */
class StateInWithEagerlyStrategyInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.statein.eagerly.display.name"
    override val descriptionKey = "inspection.statein.eagerly.description"

    private val lifecycleScopes = setOf("viewModelScope", "lifecycleScope")

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (expression.calleeExpression?.text != "stateIn") return

                val args = expression.valueArguments.mapNotNull { it.getArgumentExpression()?.text }
                if (args.none { it.contains("Eagerly") }) return
                val scopeArg = expression.valueArguments.getOrNull(0)?.getArgumentExpression()?.text ?: return
                if (lifecycleScopes.none { scopeArg.contains(it) }) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.flow.statein.eagerly"),
                    ReplaceEagerlyWithWhileSubscribedQuickFix(),
                )
            }
        }
}
