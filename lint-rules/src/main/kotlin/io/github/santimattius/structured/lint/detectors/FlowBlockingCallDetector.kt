/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Severity
import io.github.santimattius.structured.lint.utils.AndroidLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects blocking calls inside `flow { }` builder.
 *
 * Best Practice 9.1 (FLOW_001): The flow builder block runs in the collector's context.
 * Blocking calls (Thread.sleep, synchronous I/O) can freeze the wrong thread and
 * cooperate poorly with cancellation.
 *
 * See: docs/BEST_PRACTICES_COROUTINES.md#91-flow_001--blocking-code-in-flow--builder
 */
class FlowBlockingCallDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "FlowBlockingCall",
            briefDescription = "Blocking code inside flow { } builder",
            explanation = """
                [FLOW_001] Blocking calls inside flow { } run in the collector's context and can
                freeze the wrong thread (e.g. Main). Use flowOn(Dispatchers.IO) or suspend APIs
                inside the flow builder.

                See: ${LintDocUrl.buildDocLink("91-flow_001--blocking-code-in-flow--builder")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                FlowBlockingCallDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> = listOf("flow")

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        if (node.methodName != "flow") return

        val lambdaBody = getFlowLambdaBody(node) ?: return
        val blockingCalls = findBlockingCallsInLambda(lambdaBody)

        for (blockingCall in blockingCalls) {
            context.report(
                ISSUE,
                blockingCall,
                context.getLocation(blockingCall),
                "[FLOW_001] Blocking call inside flow { }. Use flowOn(Dispatchers.IO) or suspend APIs. " +
                    "See: ${LintDocUrl.buildDocLink("91-flow_001--blocking-code-in-flow--builder")}"
            )
        }
    }

    private fun getFlowLambdaBody(flowCall: UCallExpression): UExpression? {
        val arguments = flowCall.valueArguments
        if (arguments.isEmpty()) return null
        val lambdaArg = arguments.firstOrNull() ?: arguments.lastOrNull() ?: return null
        return when (lambdaArg) {
            is ULambdaExpression -> lambdaArg.body
            is UBlockExpression -> lambdaArg
            else -> null
        }
    }

    private fun findBlockingCallsInLambda(lambdaBody: UExpression): List<UCallExpression> {
        val blockingCalls = mutableListOf<UCallExpression>()
        lambdaBody.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (AndroidLintUtils.containsBlockingCall(node)) {
                    blockingCalls.add(node)
                }
                return super.visitCallExpression(node)
            }
        })
        return blockingCalls
    }
}
