/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.inspections

import com.intellij.codeInspection.ProblemsHolder
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.inspections.base.CoroutineInspectionBase
import io.github.santimattius.structured.intellij.quickfixes.ReplaceSharedFlowWithChannelQuickFix
import io.github.santimattius.structured.intellij.utils.CoroutinePsiUtils
import io.github.santimattius.structured.intellij.utils.CoroutinesImportFilter
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

/** [FLOW_011] — [MutableSharedFlow] with default buffer for one-shot UI events. */
class SharedFlowForOneshotEventsInspection : CoroutineInspectionBase() {

    override val displayNameKey = "inspection.sharedflow.oneshot.display.name"
    override val descriptionKey = "inspection.sharedflow.oneshot.description"

    private val oneShotNamePattern = Regex("""(event|command|effect)""", RegexOption.IGNORE_CASE)

    override fun buildKotlinVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid =
        object : KtVisitorVoid() {
            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                val file = property.containingKtFile
                if (!CoroutinesImportFilter.fileImportsCoroutines(file)) return
                if (CoroutinePsiUtils.looksLikeJvmTestKotlinFile(file)) return

                val propName = property.name ?: return
                if (!oneShotNamePattern.containsMatchIn(propName)) return

                val init = property.initializer as? KtCallExpression ?: return
                val callee = init.calleeExpression?.text ?: return
                if (callee != "MutableSharedFlow" && !callee.endsWith(".MutableSharedFlow")) return
                if (hasNonDefaultBuffer(init.text)) return

                holder.registerProblem(
                    property,
                    StructuredCoroutinesBundle.message("error.flow.sharedflow.oneshot"),
                    ReplaceSharedFlowWithChannelQuickFix(),
                )
            }
        }

    private fun hasNonDefaultBuffer(argsText: String): Boolean {
        val replayPositive = Regex("""replay\s*=\s*([1-9]\d*)""")
        val extraPositive = Regex("""extraBufferCapacity\s*=\s*([1-9]\d*)""")
        return replayPositive.containsMatchIn(argsText) || extraPositive.containsMatchIn(argsText)
    }
}
