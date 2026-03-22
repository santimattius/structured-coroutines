/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections.base

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Base class for all coroutine-related inspections.
 *
 * Provides common functionality and utilities for detecting
 * coroutine anti-patterns in Kotlin code.
 *
 * ### Suppression
 *
 * Every inspection in this hierarchy can be suppressed via Kotlin's `@Suppress` annotation
 * using the inspection's `shortName` (same identifier listed in SUPPRESSING_RULES.md under the
 * IntelliJ column). The short name is also accepted in `@file:Suppress` for file-wide suppression.
 *
 * ```kotlin
 * @Suppress("GlobalScopeUsage")
 * fun legacyEntryPoint() {
 *     GlobalScope.launch { }  // suppressed — no warning shown
 * }
 *
 * @file:Suppress("UnstructuredLaunch")
 * package com.example.legacy
 * ```
 *
 * Suppression is checked explicitly in [isSuppressedFor] and also by IntelliJ's built-in
 * framework (via `KotlinInspectionSuppressor`), so it works regardless of which mechanism
 * fires first.
 */
abstract class CoroutineInspectionBase : LocalInspectionTool() {

    /**
     * The key for the inspection's display name in the resource bundle.
     */
    abstract val displayNameKey: String

    /**
     * The key for the inspection's description in the resource bundle.
     */
    abstract val descriptionKey: String

    override fun getDisplayName(): String {
        return StructuredCoroutinesBundle.message(displayNameKey)
    }

    override fun getStaticDescription(): String {
        return StructuredCoroutinesBundle.message(descriptionKey)
    }

    override fun getGroupDisplayName(): String {
        return StructuredCoroutinesBundle.message("inspection.group.name")
    }

    override fun isEnabledByDefault(): Boolean = true

    /**
     * Returns true if this inspection is suppressed for [element].
     *
     * Checks for Kotlin `@Suppress("shortName")` and `@file:Suppress("shortName")` annotations
     * anywhere in the element's PSI parent chain, then falls back to the platform's default
     * suppression mechanism (which covers `// noinspection` comments and other suppression forms).
     */
    override fun isSuppressedFor(element: PsiElement): Boolean {
        return isKotlinSuppressedFor(element) || super.isSuppressedFor(element)
    }

    /**
     * Walks the PSI parent chain from [element] up to (but not including) the file root,
     * looking for a Kotlin `@Suppress("id")` annotation that matches this inspection's
     * [shortName]. Also checks `@file:Suppress` at the file level.
     */
    private fun isKotlinSuppressedFor(element: PsiElement): Boolean {
        val id = shortName
        var current: PsiElement? = element
        while (current != null && current !is PsiFile) {
            if (current is KtAnnotated && hasSuppressAnnotation(current, id)) return true
            current = current.parent
        }
        // Check @file:Suppress at file level
        val file = element.containingFile as? KtFile ?: return false
        return file.fileAnnotationList?.annotationEntries?.any { entry ->
            entry.shortName?.asString() == "Suppress" &&
            entry.valueArguments.any { arg ->
                arg.getArgumentExpression()?.text?.removeSurrounding("\"") == id
            }
        } ?: false
    }

    private fun hasSuppressAnnotation(element: KtAnnotated, id: String): Boolean {
        return element.annotationEntries.any { entry ->
            entry.shortName?.asString() == "Suppress" &&
            entry.valueArguments.any { arg ->
                arg.getArgumentExpression()?.text?.removeSurrounding("\"") == id
            }
        }
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return buildKotlinVisitor(holder, isOnTheFly)
    }

    /**
     * Creates the Kotlin-specific visitor for this inspection.
     */
    abstract fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid
}
