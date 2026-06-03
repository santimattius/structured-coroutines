/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * [INTEROP_004] — blocking [java.util.concurrent.Future.get] inside coroutines (Lint type resolution).
 */
class BlockingFutureGetDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "BlockingFutureGet",
            briefDescription = "Future.get() in coroutine",
            explanation = """
                [INTEROP_004] Use .await() from kotlinx-coroutines-jdk8 or kotlinx-coroutines-guava instead of .get().

                See: ${LintDocUrl.buildDocLink("104-interop_004--blocking-future-get")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                BlockingFutureGetDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (node.methodName != "get") return
                val method = node.resolve() ?: return
                if (!context.evaluator.isMemberInSubClassOf(method, "java.util.concurrent.Future", false)) {
                    return
                }

                val ktCall = node.sourcePsi as? KtCallExpression ?: return
                if (!isInsideCoroutinePsi(ktCall)) return
                if (AndroidLintUtils.isTestFile(context, node)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "[INTEROP_004] .get() blocks the dispatcher thread — use .await() from " +
                        "kotlinx-coroutines-jdk8 or kotlinx-coroutines-guava. " +
                        "See: ${LintDocUrl.buildDocLink("104-interop_004--blocking-future-get")}",
                )
            }
        }

    private fun isInsideCoroutinePsi(element: KtCallExpression): Boolean {
        val fn = element.getParentOfType<KtNamedFunction>(strict = true)
        if (fn?.hasModifier(KtTokens.SUSPEND_KEYWORD) == true) return true
        var current = element.parent
        while (current != null) {
            val call = current as? KtCallExpression ?: run {
                current = current.parent
                continue
            }
            val name = call.calleeExpression?.text
            if (name in setOf("launch", "async", "withContext", "coroutineScope", "supervisorScope")) {
                return true
            }
            current = current.parent
        }
        return false
    }
}
