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
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects the same Channel used with consumeEach from multiple coroutines (sibling launch/async).
 *
 * Best Practice 7.2 (CHANNEL_002): Using consumeEach from multiple coroutines on the same channel
 * cancels the channel when the first consumer finishes, breaking other consumers.
 * Use for (value in channel) in each consumer.
 */
class ConsumeEachMultipleConsumersDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ConsumeEachMultipleConsumers",
            briefDescription = "Same channel with consumeEach from multiple coroutines",
            explanation = """
                [CHANNEL_002] Using consumeEach from multiple coroutines on the same channel
                cancels the channel when the first consumer finishes, breaking other consumers.
                Use for (value in channel) in each consumer.

                See: ${LintDocUrl.buildDocLink("72-sharing-consumeeach-among-multiple-consumers")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ConsumeEachMultipleConsumersDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val BUILDER_NAMES = setOf("launch", "async")
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("launch", "async")
    }

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        if (node.methodName !in BUILDER_NAMES) return

        val containingFunction = findContainingFunction(node) ?: return
        val body = containingFunction.uastBody as? UBlockExpression ?: return

        // Only consider launch/async that are direct statements of the function body
        val directBuilderCalls = body.expressions.mapNotNull { expr ->
            when (expr) {
                is UCallExpression -> if (expr.methodName in BUILDER_NAMES) expr else null
                is UQualifiedReferenceExpression -> (expr.selector as? UCallExpression)
                    ?.takeIf { it.methodName in BUILDER_NAMES }
                else -> null
            }
        }
        val directBuilderLambdas = directBuilderCalls.mapNotNull { getLambdaBody(it) }.filterNotNull()

        // Run analysis only once per function (when we hit the first launch/async)
        val firstBuilderCall = directBuilderCalls.firstOrNull() ?: return
        if (node.sourcePsi?.textOffset != firstBuilderCall.sourcePsi?.textOffset) return

        if (directBuilderLambdas.size < 2) return

        val consumeEachReceiversByBuilder = directBuilderLambdas.map { lambda ->
            collectConsumeEachReceivers(lambda).toSet()
        }

        val allReceivers = consumeEachReceiversByBuilder.flatten()
        val duplicatedNames = allReceivers
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        if (duplicatedNames.isEmpty()) return

        // Report on each consumeEach that uses a duplicated channel name (only once per function)
        directBuilderLambdas.forEach { lambda ->
            collectConsumeEachCalls(lambda).forEach { consumeEachCall ->
                val name = getReceiverName(consumeEachCall) ?: return@forEach
                if (name in duplicatedNames) {
                    context.report(
                        ISSUE,
                        consumeEachCall,
                        context.getLocation(consumeEachCall),
                        "[CHANNEL_002] Channel '$name' is used with consumeEach from multiple coroutines. " +
                            "Use for (value in $name) in each consumer. " +
                            "See: ${LintDocUrl.buildDocLink("72-sharing-consumeeach-among-multiple-consumers")}"
                    )
                }
            }
        }
    }

    private fun findContainingFunction(call: UCallExpression): UMethod? {
        var current: UElement? = call
        while (current != null) {
            if (current is UMethod) return current
            current = current.uastParent
        }
        return null
    }

    private fun getLambdaBody(call: UCallExpression): UExpression? {
        val args = call.valueArguments
        val last = args.lastOrNull() ?: return null
        return when (last) {
            is ULambdaExpression -> last.body
            is UBlockExpression -> last
            else -> null
        }
    }

    private fun collectConsumeEachReceivers(lambdaBody: UExpression): List<String> {
        val receivers = mutableListOf<String>()
        lambdaBody.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "consumeEach") {
                    getReceiverName(node)?.let { receivers.add(it) }
                }
                return super.visitCallExpression(node)
            }
        })
        return receivers
    }

    private fun collectConsumeEachCalls(lambdaBody: UExpression): List<UCallExpression> {
        val calls = mutableListOf<UCallExpression>()
        lambdaBody.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "consumeEach") {
                    calls.add(node)
                }
                return super.visitCallExpression(node)
            }
        })
        return calls
    }

    private fun getReceiverName(call: UCallExpression): String? {
        val receiver = call.receiver ?: return null
        return when (receiver) {
            is UQualifiedReferenceExpression -> receiver.receiver.asSourceString()
            is UReferenceExpression -> receiver.asSourceString()
            else -> receiver.asSourceString()
        }
    }
}
