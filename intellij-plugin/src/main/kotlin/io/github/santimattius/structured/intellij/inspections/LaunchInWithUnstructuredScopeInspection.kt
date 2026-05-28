/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceGlobalScopeQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** [FLOW_007] — Flow.launchIn with unstructured scope. */
class LaunchInWithUnstructuredScopeInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.launchin.unstructured.display.name"
    override val descriptionKey = "inspection.launchin.unstructured.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (expression.calleeExpression?.text != "launchIn") return

                val scopeArg = expression.valueArguments.firstOrNull()?.getArgumentExpression()?.text ?: return
                val unstructured = scopeArg == "GlobalScope" ||
                    scopeArg.endsWith(".GlobalScope") ||
                    scopeArg.contains("CoroutineScope(")
                if (!unstructured) return
                if (scopeArg.contains("viewModelScope") || scopeArg.contains("lifecycleScope")) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.flow.launchin.unstructured"),
                    ReplaceGlobalScopeQuickFix.WithViewModelScope(),
                    ReplaceGlobalScopeQuickFix.WithLifecycleScope(),
                    ReplaceGlobalScopeQuickFix.WithCoroutineScope(),
                )
            }
        }
}
