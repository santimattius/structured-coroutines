/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.intellij.intentions

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiElement
import io.github.santimattius.structured.intellij.StructuredCoroutinesBundle
import io.github.santimattius.structured.intellij.utils.FlowChainAnalyzer
import org.jetbrains.kotlin.psi.KtElement

/** [INF-001] — show Flow chain analysis for the operator under the cursor. */
class AnalyzeFlowChainIntention : PsiElementBaseIntentionAction(), IntentionAction {

    override fun getText(): String = StructuredCoroutinesBundle.message("intention.analyze.flow.chain")

    override fun getFamilyName(): String = StructuredCoroutinesBundle.message("intention.category")

    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val kt = element as? KtElement ?: return false
        return FlowChainAnalyzer.findFlowDotChain(kt) != null
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val kt = element as? KtElement ?: return
        val chain = FlowChainAnalyzer.findFlowDotChain(kt) ?: return
        val report = FlowChainAnalyzer.formatReport(FlowChainAnalyzer.analyze(chain))
        Messages.showInfoMessage(project, report, StructuredCoroutinesBundle.message("intention.analyze.flow.chain"))
    }
}
