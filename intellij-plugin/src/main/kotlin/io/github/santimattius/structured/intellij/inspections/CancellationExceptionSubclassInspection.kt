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
import io.github.santimattius.structured.intellij.quickfixes.ChangeSuperclassToExceptionQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Inspection that detects classes extending CancellationException for domain errors.
 *
 * CancellationException has special semantics in coroutines: it doesn't propagate
 * like normal exceptions and only cancels the current coroutine and its children.
 * Using it for domain errors (e.g. "User not found") breaks cancellation semantics.
 *
 * Example of problematic code:
 * ```kotlin
 * class MyDomainError : CancellationException("Bad: domain error as CancellationException")
 * ```
 *
 * Use Exception or RuntimeException for domain errors instead.
 */
class CancellationExceptionSubclassInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.cancellation.subclass.display.name"
    override val descriptionKey = "inspection.cancellation.subclass.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid {
        return object : KtVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                super.visitClassOrObject(classOrObject)
                if (classOrObject !is KtClass) return
                if (!CoroutinePsiUtils.extendsCancellationException(classOrObject)) return
                val nameIdentifier = classOrObject.nameIdentifier ?: classOrObject
                holder.registerProblem(
                    nameIdentifier,
                    StructuredCoroutinesBundle.message("error.cancellation.subclass"),
                    ChangeSuperclassToExceptionQuickFix()
                )
            }
        }
    }
}
