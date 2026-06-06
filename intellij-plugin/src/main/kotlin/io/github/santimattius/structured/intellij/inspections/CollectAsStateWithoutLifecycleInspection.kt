/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceCollectAsStateWithLifecycleQuickFix
import io.github.santimattius.structured.intellij.utils.ComposePsiUtils
import io.github.santimattius.structured.intellij.utils.importsComposeRuntime
import io.github.santimattius.structured.intellij.utils.importsKotlinxCoroutinesFlow
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * [COMPOSE_001] — `Flow.collectAsState()` recomposes endlessly when inactive unless lifecycle-aware collector is used.
 */
class CollectAsStateWithoutLifecycleInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.collect.as.state.lifecycle.display.name"
    override val descriptionKey = "inspection.collect.as.state.lifecycle.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (expression.calleeExpression?.text != "collectAsState") return

                val ktFile = expression.containingKtFile
                if (!ktFile.importsComposeRuntime()) return
                if (!ktFile.importsKotlinxCoroutinesFlow()) return

                if (!ComposePsiUtils.hasComposableAncestor(expression)) return
                if (ComposePsiUtils.hasPreviewAncestor(expression)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.compose.collect.as.state"),
                    ReplaceCollectAsStateWithLifecycleQuickFix(),
                )
            }
        }
    }
}
