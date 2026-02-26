/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.quickfixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

/**
 * Quick fix to change a class superclass from CancellationException to Exception.
 */
class ChangeSuperclassToExceptionQuickFix : LocalQuickFix {

    override fun getName(): String = StructuredCoroutinesBundle.message("quickfix.change.superclass.to.exception")

    override fun getFamilyName(): String = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement
        val ktClass = element as? KtClass ?: element.getParentOfType<KtClass>(strict = false) ?: return
        val superTypeList = ktClass.superTypeListEntries.firstOrNull { it.text.contains("CancellationException") }
            ?: return
        val factory = KtPsiFactory(project)
        val newEntry = factory.createSuperTypeCallEntry("Exception()")
        superTypeList.replace(newEntry)
    }
}
