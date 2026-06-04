/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceWithLaunchedEffectQuickFix
import io.github.santimattius.structured.intellij.utils.ComposePsiUtils
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import io.github.santimattius.structured.intellij.utils.importsComposeRuntime
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** [COMPOSE_002] — [rememberCoroutineScope] used for init work in composable body. */
class RememberScopeForInitInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.remember.scope.for.init.display.name"
    override val descriptionKey = "inspection.remember.scope.for.init.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (expression.calleeExpression?.text != "launch") return
                if (!CoroutinePsiUtils.isFrameworkScopeCall(expression)) return

                val file = expression.containingKtFile
                if (!file.importsComposeRuntime()) return
                if (!ComposePsiUtils.hasComposableAncestor(expression)) return
                if (ComposePsiUtils.hasPreviewAncestor(expression)) return
                if (ComposePsiUtils.isInsideComposeEffectOrEventHandler(expression)) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.compose.remember.scope.for.init"),
                    ReplaceWithLaunchedEffectQuickFix(),
                )
            }
        }
}
