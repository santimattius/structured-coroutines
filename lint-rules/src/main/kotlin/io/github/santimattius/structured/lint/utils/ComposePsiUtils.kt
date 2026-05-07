/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.utils

import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction

fun KtFile.importsComposeRuntime(): Boolean =
    importDirectives.any {
        val fq = it.importedFqName?.asString().orEmpty()
        fq.startsWith("androidx.compose.runtime")
    }

fun KtFile.importsKotlinxCoroutinesFlow(): Boolean =
    importDirectives.any {
        val fq = it.importedFqName?.asString().orEmpty()
        fq.startsWith("kotlinx.coroutines.flow")
    }

/**
 * Kotlin PSI helpers for Jetpack Compose ([COMPOSE_001]).
 * Keep aligned with IntelliJ `ComposePsiUtils`.
 *
 * Import checks are top-level extensions so detectors use `ktFile.importsComposeRuntime()` (same style as `KotlinPsiLintUtils`).
 */
object ComposePsiUtils {

    /** True if `@Preview` / `@MultiPreview` is on an enclosing composable declaration/lambda. */
    fun hasPreviewAncestor(element: KtElement): Boolean =
        enclosingAnnotatedElements(element).any { annotated ->
            annotated.annotationEntries.any { entry ->
                when (entry.shortName?.asString()) {
                    "Preview", "MultiPreview" -> true
                    else -> false
                }
            }
        }

    /** True when an enclosing declaration/lambda carries `@Composable`. */
    fun hasComposableAncestor(element: KtElement): Boolean =
        enclosingAnnotatedElements(element).any { annotated ->
            annotated.annotationEntries.any { it.shortName?.asString() == "Composable" }
        }

    private fun enclosingAnnotatedElements(element: KtElement): List<KtAnnotated> {
        val result = mutableListOf<KtAnnotated>()
        var p: KtElement? = element.parent as? KtElement
        while (p != null) {
            when (p) {
                is KtNamedFunction -> result += p
                is KtLambdaExpression -> result += p.functionLiteral
                else -> { }
            }
            p = p.parent as? KtElement
        }
        return result
    }
}
