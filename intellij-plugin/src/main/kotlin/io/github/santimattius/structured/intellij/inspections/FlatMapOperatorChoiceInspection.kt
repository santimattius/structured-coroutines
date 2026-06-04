/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** [FLOW_009] — contextual guidance for flatMap operator choice (Info). */
class FlatMapOperatorChoiceInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.flatmap.operator.choice.display.name"
    override val descriptionKey = "inspection.flatmap.operator.choice.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return

                val callee = expression.calleeExpression?.text ?: return
                val context = expression.parentsWithSelf().joinToString(" ") { it.text }

                when {
                    callee == "flatMapLatest" && looksLikeDownloadContext(context) -> {
                        holder.registerProblem(
                            expression,
                            StructuredCoroutinesBundle.message("error.flow.flatmap.latest.download"),
                        )
                    }
                    callee == "flatMapConcat" && looksLikeSearchContext(context) -> {
                        holder.registerProblem(
                            expression,
                            StructuredCoroutinesBundle.message("error.flow.flatmap.concat.search"),
                        )
                    }
                }
            }
        }

    private fun org.jetbrains.kotlin.psi.KtElement.parentsWithSelf(): Sequence<org.jetbrains.kotlin.psi.KtElement> =
        generateSequence(this as org.jetbrains.kotlin.psi.KtElement?) { it.parent as? org.jetbrains.kotlin.psi.KtElement }

    private fun looksLikeDownloadContext(text: String): Boolean =
        listOf("download", "Download", "upload", "Upload", "file").any { text.contains(it) }

    private fun looksLikeSearchContext(text: String): Boolean =
        listOf("searchQuery", "searchText", "query", "Query", "search").any { text.contains(it) }
}
