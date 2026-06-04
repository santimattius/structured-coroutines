/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.isAncestor

/** [TEST_005] — hardcoded [Dispatchers.IO]/[Dispatchers.Main] in production classes. */
class HardcodedDispatcherInClassInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.hardcoded.dispatcher.display.name"
    override val descriptionKey = "inspection.hardcoded.dispatcher.description"

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                super.visitDotQualifiedExpression(expression)
                if (!CoroutinesImportFilter.fileImportsCoroutines(expression.containingKtFile)) return
                if (CoroutinePsiUtils.looksLikeJvmTestKotlinFile(expression.containingKtFile)) return

                val receiver = expression.receiverExpression.text.trim()
                if (receiver != "Dispatchers" && !receiver.endsWith(".Dispatchers")) return
                val selector = expression.selectorExpression?.text ?: return
                if (selector != "IO" && selector != "Main") return
                if (!isInsideCoroutine(expression)) return
                if (isInDefaultParameterValue(expression)) return

                val klass = expression.getParentOfType<KtClass>(strict = true) ?: return
                if (klass.hasDispatcherQualifierParameter()) return

                holder.registerProblem(
                    expression,
                    StructuredCoroutinesBundle.message("error.test.hardcoded.dispatcher"),
                )
            }
        }

    private fun isInsideCoroutine(element: KtDotQualifiedExpression): Boolean {
        val fn = element.getParentOfType<KtNamedFunction>(strict = true)
        if (fn?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return true
        var current = element.parent
        while (current != null) {
            val call = current as? org.jetbrains.kotlin.psi.KtCallExpression
            if (call?.calleeExpression?.text in setOf("launch", "async", "withContext", "coroutineScope", "supervisorScope")) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun isInDefaultParameterValue(element: KtDotQualifiedExpression): Boolean {
        val parameter = element.getParentOfType<KtParameter>(strict = true) ?: return false
        val default = parameter.defaultValue ?: return false
        return default.isAncestor(element)
    }

    private fun KtClass.hasDispatcherQualifierParameter(): Boolean {
        val params = primaryConstructor?.valueParameters.orEmpty() +
            secondaryConstructors.flatMap { it.valueParameters }
        return params.any { param ->
            param.annotationEntries.any { ann ->
                val short = ann.shortName?.asString()
                short == "IoDispatcher" || short == "MainDispatcher"
            }
        }
    }
}
