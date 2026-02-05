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
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiFile

/**
 * A [ProblemsHolder] that collects all registered [ProblemDescriptor]s so they can be
 * displayed in the Structured Coroutines tool window.
 */
class CollectingProblemsHolder(
    manager: InspectionManager,
    file: PsiFile,
    isOnTheFly: Boolean
) : ProblemsHolder(manager, file, isOnTheFly) {

    val collectedProblems: MutableList<ProblemDescriptor> = mutableListOf()

    override fun registerProblem(descriptor: ProblemDescriptor) {
        collectedProblems.add(descriptor)
        super.registerProblem(descriptor)
    }
}
