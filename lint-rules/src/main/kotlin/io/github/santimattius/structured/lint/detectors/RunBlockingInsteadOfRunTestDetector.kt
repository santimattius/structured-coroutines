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
import io.github.santimattius.structured.lint.utils.hasShortNameTestAnnotation
import io.github.santimattius.structured.lint.utils.importsKotlinxCoroutines
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * [TEST_004] — `@Test fun x() = runBlocking { }` should use `runTest` from kotlinx-coroutines-test.
 *
 * When `runBlocking` contains `delay()`, [RunBlockingWithDelayInTestDetector] ([TEST_001]) reports instead.
 */
class RunBlockingInsteadOfRunTestDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "RunBlockingInsteadOfRunTest",
            briefDescription = "runBlocking as JUnit test body",
            explanation = """
                [TEST_004] Using `runBlocking { }` as the direct body of a `@Test` function misses the
                structured test scope and virtual time provided by `runTest` from kotlinx-coroutines-test.

                See: ${LintDocUrl.buildDocLink("64-test_004--runblocking-instead-of-runtest")}
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                RunBlockingInsteadOfRunTestDetector::class.java,
                Scope.JAVA_FILE_SCOPE,
            ),
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("runBlocking")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod,
    ) {
        if (!AndroidLintUtils.isTestFile(context, node)) return

        val ktCall = node.sourcePsi as? KtCallExpression ?: return
        val ktFile = ktCall.containingKtFile
        if (!ktFile.importsKotlinxCoroutines()) return

        val enclosing = ktCall.getParentOfType<KtNamedFunction>(strict = false) ?: return
        if (enclosing.bodyExpression !== ktCall) return
        if (!enclosing.hasShortNameTestAnnotation()) return

        val lambdaBody = getLambdaBody(node) ?: return
        if (containsDelayCall(lambdaBody)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "[TEST_004] Prefer `runTest { }` instead of `runBlocking { }` as the test body for coroutine-aware tests. " +
                "See: ${LintDocUrl.buildDocLink("64-test_004--runblocking-instead-of-runtest")}",
        )
    }

    private fun getLambdaBody(call: UCallExpression): UExpression? {
        val arguments = call.valueArguments
        if (arguments.isEmpty()) return null
        val lastArg = arguments.lastOrNull() ?: return null
        return when (lastArg) {
            is ULambdaExpression -> lastArg.body
            is UBlockExpression -> lastArg
            else -> null
        }
    }

    private fun containsDelayCall(expression: UExpression): Boolean {
        var foundDelay = false
        expression.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "delay") {
                    foundDelay = true
                    return false
                }
                return super.visitCallExpression(node)
            }
        })
        return foundDelay
    }
}
