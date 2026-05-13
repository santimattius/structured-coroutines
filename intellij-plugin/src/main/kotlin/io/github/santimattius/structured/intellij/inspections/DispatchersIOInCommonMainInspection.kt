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
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import io.github.santimattius.structured.intellij.utils.KotlinCommonSourcePsiUtils
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * [KMP_001] — `Dispatchers.IO` in `commonMain` / `commonTest` sources (virtual path heuristic).
 */
class DispatchersIOInCommonMainInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.dispatchers.io.common.display.name"
    override val descriptionKey = "inspection.dispatchers.io.common.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)
                val file = expression.containingKtFile
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return
                val path = file.virtualFilePath
                if (!KotlinCommonSourcePsiUtils.looksLikeKotlinCommonVirtualPath(path)) return

                val condensed = expression.text.replace("\\s+".toRegex(), "")
                    .removePrefix("kotlinx.coroutines.")
                if (condensed != "Dispatchers.IO" && !condensed.endsWith(".Dispatchers.IO")) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.kmp.dispatchers.io.common"),
                )
            }
        }
}
