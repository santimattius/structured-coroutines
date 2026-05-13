/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import io.github.santimattius.structured.lint.utils.AndroidLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import io.github.santimattius.structured.lint.utils.MissingCatchInFlowHeuristic
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.uast.UCallExpression
/**
 * [FLOW_005] — Android Lint facade over the same heuristic as Detekt (`MissingCatchInFlow`).
 */
class MissingCatchInFlowDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "MissingCatchInFlow",
            briefDescription = "Flow collector without upstream catch operator",
            explanation = """
                [FLOW_005] Long chains that transform or filter Flow emissions rarely surface errors safely.
                Prefer `.catch { }` (or deliberate error propagation) upstream of `.collect` / `.launchIn`
                unless the whole collector is guarded by `try/catch(Throwable)`.

                See: ${LintDocUrl.buildDocLink("96-flow_005--missing-catch-in-flow-chain")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                MissingCatchInFlowDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )

        fun importsCoroutinesOrFlow(file: KtFile): Boolean =
            file.importDirectives.any {
                val fq = it.importedFqName?.asString().orEmpty()
                fq.startsWith("kotlinx.coroutines")
            }
    }

    override fun getApplicableMethodNames(): List<String> = listOf("collect", "collectLatest", "launchIn")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod,
    ) {
        if (AndroidLintUtils.isTestFile(context, node)) return

        val ktCall = node.sourcePsi as? KtCallExpression ?: return
        val ktFile = ktCall.containingKtFile
        if (!importsCoroutinesOrFlow(ktFile)) return

        if (!MissingCatchInFlowHeuristic.shouldReportTerminalCall(ktCall)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "[FLOW_005] Consider `.catch { }` before the terminal collector for transformed Flow chains. " +
                "See: ${LintDocUrl.buildDocLink("96-flow_005--missing-catch-in-flow-chain")}",
        )
    }
}
