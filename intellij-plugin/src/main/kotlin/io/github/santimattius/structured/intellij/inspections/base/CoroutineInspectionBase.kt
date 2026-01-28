/**
 * Copyright 2024 Santiago Mattiauda
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
import com.intellij.psi.PsiElementVisitor
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Base class for all coroutine-related inspections.
 *
 * Provides common functionality and utilities for detecting
 * coroutine anti-patterns in Kotlin code.
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

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return buildKotlinVisitor(holder, isOnTheFly)
    }

    /**
     * Creates the Kotlin-specific visitor for this inspection.
     */
    abstract fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid
}
