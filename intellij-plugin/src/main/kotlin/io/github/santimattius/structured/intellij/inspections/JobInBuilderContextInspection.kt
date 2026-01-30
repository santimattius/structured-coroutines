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
import io.github.santimattius.structured.intellij.quickfixes.ReplaceJobWithSupervisorScopeQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects Job() or SupervisorJob() in coroutine builder context.
 *
 * Passing a new Job() or SupervisorJob() to coroutine builders like launch or async
 * breaks structured concurrency because the new job is not connected to the parent's
 * job hierarchy, making cancellation propagation unreliable.
 *
 * Example of problematic code:
 * ```kotlin
 * scope.launch(Job()) {
 *     fetchData()  // Won't be cancelled when parent scope is cancelled
 * }
 *
 * scope.launch(SupervisorJob()) {
 *     fetchData()  // Same problem
 * }
 * ```
 *
 * Recommended fix:
 * ```kotlin
 * // For independent child failure handling, use supervisorScope
 * supervisorScope {
 *     launch { task1() }  // Failures don't affect siblings
 *     launch { task2() }
 * }
 * ```
 */
class JobInBuilderContextInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.job.in.builder.display.name"
    override val descriptionKey = "inspection.job.in.builder.description"

    private val coroutineBuilders = setOf("launch", "async")

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                val calleeName = expression.calleeExpression?.text ?: return
                if (calleeName !in coroutineBuilders) return

                // Check if Job() or SupervisorJob() is passed as argument
                if (CoroutinePsiUtils.usesJobInContext(expression)) {
                    val jobType = findJobType(expression)

                    holder.registerProblem(
                        expression,
                        StructuredCoroutinesBundle.message("error.job.in.builder", jobType),
                        ReplaceJobWithSupervisorScopeQuickFix()
                    )
                }
            }
        }
    }

    private fun findJobType(call: KtCallExpression): String {
        for (arg in call.valueArguments) {
            val argText = arg.text
            when {
                argText.contains("SupervisorJob()") -> return "SupervisorJob"
                argText.contains("Job()") -> return "Job"
            }
        }
        return "Job"
    }
}
