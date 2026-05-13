/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceWithBackingPropertyQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import io.github.santimattius.structured.intellij.utils.ComposePsiUtils
import org.jetbrains.kotlin.lexer.KtModifierKeywordToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * [FLOW_010] — `MutableStateFlow` or `MutableSharedFlow` exposed as a public property breaks
 * encapsulation. Consumers should only see the read-only interface.
 *
 * Detects `public val` whose declared or inferred type contains `MutableStateFlow` or
 * `MutableSharedFlow`.
 *
 * Exclusions:
 * - Properties inside test objects (class names ending in `Test`/`Tests`/`Spec`).
 * - Properties inside `@Preview` annotated composable functions.
 */
class MutableFlowExposedInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.mutable.flow.exposed.display.name"
    override val descriptionKey = "inspection.mutable.flow.exposed.description"

    private val mutableFlowTypes = setOf("MutableStateFlow", "MutableSharedFlow")

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)

                // Only flag public properties
                if (!property.isPublic()) return

                val file = property.containingKtFile
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return

                // Check declared type annotation
                val typeRef = property.typeReference?.text ?: ""
                val initializerText = property.initializer?.text ?: ""

                val typeIsMutableFlow = mutableFlowTypes.any { mutableType ->
                    typeRef.contains(mutableType) || initializerText.startsWith(mutableType)
                }

                if (!typeIsMutableFlow) return

                // Exclusion: inside test classes
                if (isInsideTestClass(property)) return

                // Exclusion: inside @Preview composables
                if (ComposePsiUtils.hasPreviewAncestor(property)) return

                holder.registerProblem(
                    property,
                    StructuredCoroutinesBundle.message("error.flow.mutable.flow.exposed"),
                    ReplaceWithBackingPropertyQuickFix()
                )
            }
        }

    private fun KtProperty.isPublic(): Boolean {
        // A property is public if it has no visibility modifier (default is public)
        // or has an explicit `public` modifier
        val hasPrivate = hasModifier(KtTokens.PRIVATE_KEYWORD)
        val hasProtected = hasModifier(KtTokens.PROTECTED_KEYWORD)
        val hasInternal = hasModifier(KtTokens.INTERNAL_KEYWORD)
        return !hasPrivate && !hasProtected && !hasInternal
    }

    private fun isInsideTestClass(property: KtProperty): Boolean {
        var parent = property.parent
        while (parent != null) {
            when (parent) {
                is KtClass -> {
                    val name = parent.name ?: return false
                    return name.endsWith("Test") || name.endsWith("Tests") || name.endsWith("Spec")
                }
                is KtObjectDeclaration -> {
                    val name = parent.name ?: return false
                    return name.endsWith("Test") || name.endsWith("Tests") || name.endsWith("Spec")
                }
            }
            parent = parent.parent
        }
        return false
    }
}
