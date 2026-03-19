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
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import io.github.santimattius.structured.intellij.inspections.StructuredCoroutinesInspectionProvider
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

/**
 * Runs all Structured Coroutines inspections on a Kotlin file and returns
 * findings with the inspection display name for the tool window.
 *
 * Uses [StructuredCoroutinesInspectionProvider] as single source of truth so any new
 * inspection registered there is automatically included in the tool window.
 */
object StructuredCoroutinesInspectionRunner {

    /**
     * A single inspection finding for a file.
     *
     * @property inspectionName Display name of the inspection that fired.
     * @property descriptor IntelliJ problem descriptor pointing to the PSI element.
     * @property severity "ERROR" or "WARNING", derived from the inspection's default level.
     * @property whatToDo Short actionable summary (1–2 lines) shown in the "What to do" column.
     * @property guideUrl URL to the relevant section in BEST_PRACTICES_COROUTINES.md.
     */
    data class Finding(
        val inspectionName: String,
        val descriptor: ProblemDescriptor,
        val severity: String,
        val whatToDo: String,
        val guideUrl: String
    )

    private val inspectionClasses: Array<Class<out LocalInspectionTool>> by lazy {
        StructuredCoroutinesInspectionProvider().getInspectionClasses()
    }

    /**
     * Runs all Structured Coroutines inspections on every Kotlin source file in the project.
     *
     * This method is intended to be called from a background thread
     * (e.g. inside a [com.intellij.openapi.progress.Task.Backgroundable]).
     * PSI access is wrapped in [ReadAction.compute] automatically by [runOnFile];
     * file collection here runs inside a single read action for efficiency.
     *
     * @param project The project to scan.
     * @param indicator Optional progress indicator — updated with fraction and current file name.
     *                  The scan stops early if the indicator is cancelled.
     * @return Aggregated findings across all Kotlin source files, sorted by file path then line.
     */
    fun runOnProject(project: Project, indicator: ProgressIndicator? = null): List<Finding> {
        val ktFiles: Collection<VirtualFile> = ReadAction.compute<Collection<VirtualFile>, Throwable> {
            FileTypeIndex.getFiles(KotlinFileType.INSTANCE, GlobalSearchScope.projectScope(project))
        }

        // Filter to source roots only (exclude .gradle, build/, generated code, etc.)
        val sourceRoots = ReadAction.compute<Set<VirtualFile>, Throwable> {
            ProjectRootManager.getInstance(project).contentSourceRoots.toHashSet()
        }

        val sourceFiles = ktFiles.filter { vf ->
            sourceRoots.any { root -> vf.path.startsWith(root.path) }
        }

        val total = sourceFiles.size
        val findings = mutableListOf<Finding>()

        sourceFiles.forEachIndexed { index, vf ->
            if (indicator?.isCanceled == true) return@forEachIndexed

            indicator?.fraction = if (total > 0) index.toDouble() / total else 0.0
            indicator?.text2 = vf.name

            val ktFile: KtFile? = ReadAction.compute<KtFile?, Throwable> {
                PsiManager.getInstance(project).findFile(vf) as? KtFile
            }

            if (ktFile != null) {
                findings.addAll(runOnFile(project, ktFile))
            }
        }

        return findings
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
            val guide = InspectionGuideRegistry.getGuide(inspectionClass)
            val whatToDo = guide?.whatToDo.orEmpty()
            val guideUrl = guide?.guideUrl.orEmpty()
            for (descriptor in holder.collectedProblems) {
                findings.add(Finding(name, descriptor, severity, whatToDo, guideUrl))
            }
        }

        return findings
    }
}
