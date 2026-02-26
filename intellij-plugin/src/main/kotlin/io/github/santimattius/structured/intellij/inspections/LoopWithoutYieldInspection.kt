/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.AddCooperationPointInLoopQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtForExpression
import org.jetbrains.kotlin.psi.KtLoopExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhileExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Inspection that detects loops without cooperation points in suspend functions.
 *
 * Long-running loops in suspend functions without yield(), ensureActive(), or delay()
 * cannot be cancelled until the loop completes.
 */
class LoopWithoutYieldInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.loop.without.yield.display.name"
    override val descriptionKey = "inspection.loop.without.yield.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitForExpression(expression: KtForExpression) {
                super.visitForExpression(expression)
                checkLoop(expression, holder)
            }

            override fun visitWhileExpression(expression: KtWhileExpression) {
                super.visitWhileExpression(expression)
                checkLoop(expression, holder)
            }
        }
    }

    private fun checkLoop(loop: KtLoopExpression, holder: ProblemsHolder) {
        if (!CoroutinePsiUtils.isInSuspendFunction(loop)) return
        val body = loop.body ?: return
        val calls = body.collectDescendantsOfType<KtCallExpression>()
        val hasCooperationPoint = calls.any { call ->
            call.calleeExpression?.text in CoroutinePsiUtils.COOPERATION_POINTS || CoroutinePsiUtils.isSuspendCall(call)
        }
        if (!hasCooperationPoint) {
            val insideScope = CoroutinePsiUtils.isInsideScopeBuilderBlock(loop)
            val fixes = buildList {
                if (insideScope) {
                    add(AddCooperationPointInLoopQuickFix("kotlinx.coroutines.ensureActive()", "quickfix.add.ensure.active.in.loop"))
                } else {
                    add(AddCooperationPointInLoopQuickFix("kotlinx.coroutines.currentCoroutineContext().ensureActive()", "quickfix.add.current.coroutine.context.ensure.active.in.loop"))
                }
                add(AddCooperationPointInLoopQuickFix("kotlinx.coroutines.yield()", "quickfix.add.yield.in.loop"))
                add(AddCooperationPointInLoopQuickFix("kotlinx.coroutines.delay(0)", "quickfix.add.delay.in.loop"))
            }
            holder.registerProblem(loop, StructuredCoroutinesBundle.message("error.loop.without.yield"), *fixes.toTypedArray())
        }
    }
}
