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
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.santimattius.structured.intellij.inspections.StructuredCoroutinesInspectionProvider
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import org.jetbrains.kotlin.psi.KtFile

/**
 * Runs all Structured Coroutines inspections on a Kotlin file and returns
 * findings with the inspection display name for the tool window.
 *
 * Uses [StructuredCoroutinesInspectionProvider] as single source of truth so any new
 * inspection registered there is automatically included in the tool window.
 */
object StructuredCoroutinesInspectionRunner {

    data class Finding(
        val inspectionName: String,
        val descriptor: ProblemDescriptor,
        val severity: String
    )

    private val inspectionClasses: Array<Class<out LocalInspectionTool>> by lazy {
        StructuredCoroutinesInspectionProvider().getInspectionClasses()
    }

    fun runOnFile(project: Project, file: KtFile): List<Finding> {
        val manager = InspectionManager.getInstance(project)
        val findings = mutableListOf<Finding>()

        for (inspectionClass in inspectionClasses) {
            val inspection = inspectionClass.getDeclaredConstructor().newInstance()
            if (inspection !is CoroutineInspectionBase) continue
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
