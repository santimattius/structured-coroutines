/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.view

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.santimattius.structured.intellij.inspections.AsyncWithoutAwaitInspection
import io.github.santimattius.structured.intellij.inspections.CancellationExceptionSwallowedInspection
import io.github.santimattius.structured.intellij.inspections.DispatchersUnconfinedInspection
import io.github.santimattius.structured.intellij.inspections.GlobalScopeInspection
import io.github.santimattius.structured.intellij.inspections.InlineCoroutineScopeInspection
import io.github.santimattius.structured.intellij.inspections.JobInBuilderContextInspection
import io.github.santimattius.structured.intellij.inspections.MainDispatcherMisuseInspection
import io.github.santimattius.structured.intellij.inspections.RunBlockingInSuspendInspection
import io.github.santimattius.structured.intellij.inspections.ScopeReuseAfterCancelInspection
import io.github.santimattius.structured.intellij.inspections.SuspendInFinallyInspection
import io.github.santimattius.structured.intellij.inspections.UnstructuredLaunchInspection
import org.jetbrains.kotlin.psi.KtFile

/**
 * Runs all Structured Coroutines inspections on a Kotlin file and returns
 * findings with the inspection display name for the tool window.
 */
object StructuredCoroutinesInspectionRunner {

    data class Finding(
        val inspectionName: String,
        val descriptor: ProblemDescriptor,
        val severity: String
    )

    private val inspectionClasses = listOf(
        GlobalScopeInspection::class.java,
        MainDispatcherMisuseInspection::class.java,
        ScopeReuseAfterCancelInspection::class.java,
        RunBlockingInSuspendInspection::class.java,
        UnstructuredLaunchInspection::class.java,
        AsyncWithoutAwaitInspection::class.java,
        InlineCoroutineScopeInspection::class.java,
        JobInBuilderContextInspection::class.java,
        SuspendInFinallyInspection::class.java,
        CancellationExceptionSwallowedInspection::class.java,
        DispatchersUnconfinedInspection::class.java
    )

    fun runOnFile(project: Project, file: KtFile): List<Finding> {
        val manager = InspectionManager.getInstance(project)
        val findings = mutableListOf<Finding>()

        for (inspectionClass in inspectionClasses) {
            @Suppress("UNCHECKED_CAST")
            val inspection = inspectionClass.getDeclaredConstructor().newInstance() as io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
            val holder = CollectingProblemsHolder(manager, file, true)
            val visitor = inspection.buildKotlinVisitor(holder, true)
            // KtVisitorVoid is non-recursive; only visitKtFile would be called with file.accept(visitor).
            // Wrap in a tree visitor so the whole file is traversed and visitCallExpression etc. are invoked.
            val treeVisitor = KtTreeTraversingVisitor(visitor)
            file.accept(treeVisitor)
            val name = inspection.displayName
            val severity = if (inspection.defaultLevel.name == "ERROR") "ERROR" else "WARNING"
            for (descriptor in holder.collectedProblems) {
                findings.add(Finding(name, descriptor, severity))
            }
        }

        return findings
    }
}
