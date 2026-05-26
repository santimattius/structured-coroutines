/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/**
 * [CONCUR_001] — synchronized inside suspend functions or coroutine builders.
 */
class SynchronizedInCoroutineDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "SynchronizedInCoroutine",
            briefDescription = "synchronized inside coroutine",
            explanation = """
                [CONCUR_001] synchronized blocks the dispatcher thread. Use Mutex.withLock in coroutines.

                See: ${LintDocUrl.buildDocLink("121-concur_001--synchronizedincoroutine")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.WARNING,
            implementation = Implementation(
                SynchronizedInCoroutineDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val ktCall = node.sourcePsi as? KtCallExpression ?: return
                if (ktCall.calleeExpression?.text != "synchronized") return
                if (!isInsideCoroutinePsi(ktCall)) return
                if (isInsideMutexWithLock(ktCall)) return

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "[CONCUR_001] synchronized() inside a coroutine blocks the dispatcher thread. " +
                        "Use Mutex.withLock { }. " +
                        "See: ${LintDocUrl.buildDocLink("121-concur_001--synchronizedincoroutine")}",
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

    private fun isInsideMutexWithLock(element: KtCallExpression): Boolean {
        var current = element.parent
        while (current != null) {
            val call = current as? KtCallExpression
            if (call?.calleeExpression?.text == "withLock") return true
            current = current.parent
        }
        return false
    }
}
