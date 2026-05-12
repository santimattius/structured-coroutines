/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Splits a public [kotlinx.coroutines.flow.MutableStateFlow] / [MutableSharedFlow] property into
 * a private mutable backing field and a read-only [kotlinx.coroutines.flow.StateFlow] / [SharedFlow].
 */
class ReplaceWithBackingPropertyQuickFix : LocalQuickFix {

    override fun getName(): String =
        StructuredCoroutinesBundle.message("quickfix.replace.with.backing.property")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val prop = descriptor.psiElement as? KtProperty ?: return
        val name = prop.name ?: return
        val initializer = prop.initializer?.text ?: return
        val factory = KtPsiFactory(project, markGenerated = false)
        val backing = "_$name"
        val readOnly =
            if (initializer.contains("MutableSharedFlow")) "asSharedFlow()"
            else "asStateFlow()"
        val privateProp = factory.createProperty("private val $backing = $initializer")
        val publicProp = factory.createProperty("val $name = $backing.$readOnly")
        prop.replace(privateProp)
        privateProp.parent?.addAfter(publicProp, privateProp)
    }
}
