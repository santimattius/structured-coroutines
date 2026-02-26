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
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Inspection that detects Flow collection in lifecycleScope without
 * repeatOnLifecycle or flowWithLifecycle (ARCH_002 / §8.2).
 *
 * Collecting a Flow in lifecycleScope.launch { flow.collect { } } keeps the flow
 * running when the UI goes to background. Use repeatOnLifecycle(Lifecycle.State.STARTED)
 * or flowWithLifecycle so collection cancels when the lifecycle stops.
 */
class LifecycleAwareFlowCollectionInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.lifecycle.flow.collection.display.name"
    override val descriptionKey = "inspection.lifecycle.flow.collection.description"

    companion object {
        private val LAUNCH_METHODS = setOf("launch", "launchWhenStarted", "launchWhenCreated", "launchWhenResumed")
        private val COLLECT_METHODS = setOf("collect", "collectLatest", "collectIndexed")
        private val LIFECYCLE_SAFE_METHODS = setOf("repeatOnLifecycle", "flowWithLifecycle")
    }

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val callee = expression.calleeExpression?.text ?: return
                if (callee !in LAUNCH_METHODS) return

                val scopeName = CoroutinePsiUtils.getScopeName(expression) ?: return
                if (scopeName != "lifecycleScope" && !scopeName.endsWith(".lifecycleScope")) return

                val containingClass = expression.getParentOfType<KtClass>(strict = false) ?: return
                if (!CoroutinePsiUtils.isLifecycleOwnerClass(containingClass)) return

                val lambdaArg = expression.valueArguments.filterIsInstance<KtLambdaArgument>().firstOrNull()
                    ?: expression.lambdaArguments.firstOrNull() ?: return
                val body = lambdaArg.getLambdaExpression()?.bodyExpression ?: return

                val callsInBody = body.collectDescendantsOfType<KtCallExpression>()
                if (!callsInBody.any { it.calleeExpression?.text in COLLECT_METHODS }) return
                if (callsInBody.any { it.calleeExpression?.text in LIFECYCLE_SAFE_METHODS }) return

                val elementToHighlight = (expression.parent as? KtDotQualifiedExpression) ?: expression
                holder.registerProblem(
                    elementToHighlight,
                    StructuredCoroutinesBundle.message("error.lifecycle.flow.collection")
                )
            }
        }
    }
}
