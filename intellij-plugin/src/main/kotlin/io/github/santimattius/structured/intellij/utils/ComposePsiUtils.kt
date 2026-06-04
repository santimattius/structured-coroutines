/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.intellij.utils

import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

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
 * Keep aligned with `lint-rules/.../ComposePsiUtils.kt`.
 *
 * Import checks live as top-level [KtFile] extensions so callers use `file.importsComposeRuntime()`.
 */
object ComposePsiUtils {

    private const val COMPOSABLE_FQN = "androidx.compose.runtime.Composable"
    private const val PREVIEW_FQN = "androidx.compose.ui.tooling.preview.Preview"
    private const val MULTI_PREVIEW_FQN = "androidx.compose.ui.tooling.preview.MultiPreview"

    fun hasPreviewAncestor(element: KtElement): Boolean {
        val ktFile = element.containingFile as? KtFile ?: return false
        val previewNames = buildSet {
            add("Preview")
            add("MultiPreview")
            addAll(ktFile.aliasNamesFor(PREVIEW_FQN))
            addAll(ktFile.aliasNamesFor(MULTI_PREVIEW_FQN))
        }
        return enclosingAnnotatedElements(element).any { annotated ->
            annotated.annotationEntries.any { it.shortName?.asString() in previewNames }
        }
    }

    fun hasComposableAncestor(element: KtElement): Boolean {
        val ktFile = element.containingFile as? KtFile ?: return false
        val composableNames = buildSet {
            add("Composable")
            addAll(ktFile.aliasNamesFor(COMPOSABLE_FQN))
        }
        return enclosingAnnotatedElements(element).any { annotated ->
            annotated.annotationEntries.any { it.shortName?.asString() in composableNames }
        }
    }

    private fun KtFile.aliasNamesFor(fqName: String): List<String> =
        importDirectives.mapNotNull { directive ->
            if (directive.importedFqName?.asString() != fqName) return@mapNotNull null
            directive.alias?.name
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

    private val composeEffectCalls = setOf("SideEffect", "LaunchedEffect", "DisposableEffect")
    private val eventHandlerParamSuffixes = listOf("onClick", "onValueChange", "onDismiss", "Listener", "Handler")

    fun isInsideComposeEffectOrEventHandler(element: KtElement): Boolean =
        isInsideComposeEffectBlock(element) || isInsideEventHandlerLambda(element)

    fun isInsideComposeEffectBlock(element: KtElement): Boolean {
        var current: KtElement? = element
        while (current != null) {
            val lambda = current.getParentOfType<KtLambdaExpression>(strict = true)
            if (lambda != null) {
                val call = lambda.getParentOfType<KtCallExpression>(strict = false)
                if (call?.calleeExpression?.text in composeEffectCalls) return true
            }
            current = current.parent as? KtElement
        }
        return false
    }

    fun isInsideEventHandlerLambda(element: KtElement): Boolean {
        val lambda = element.getParentOfType<KtLambdaExpression>(strict = true) ?: return false
        val valueArg = lambda.getParentOfType<KtValueArgument>(strict = true) ?: return false
        val name = valueArg.getArgumentName()?.asName?.asString()
            ?: valueArg.getArgumentName()?.text
            ?: return false
        return eventHandlerParamSuffixes.any { suffix -> name == suffix || name.endsWith(suffix) }
    }
}
