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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Severity
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects manual Channel() creation without a corresponding close() call in the same function.
 *
 * Best Practice 7.1 (CHANNEL_001): Creating a Channel manually and never calling close()
 * can cause consumers using `for (x in channel)` to block forever.
 *
 * Use produce { } which closes the channel when the coroutine terminates, or ensure close() is called.
 */
class ChannelNotClosedDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "ChannelNotClosed",
            briefDescription = "Manual Channel without close()",
            explanation = """
                [CHANNEL_001] Creating a Channel manually and never calling close() can cause
                consumers using for (x in channel) to block forever. Use produce { } or ensure
                close() is called in the same function.

                See: ${LintDocUrl.buildDocLink("71-forgetting-to-close-manual-channels")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChannelNotClosedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val name = node.methodName ?: return
                if (name != "Channel" && !name.startsWith("Channel<")) return

                if (isInsideProduce(node)) return

        val variableName = getVariableNameFromChannelCall(node) ?: return
        val containingFunction = findContainingFunction(node) ?: return
        val functionBody = containingFunction.uastBody ?: return

        val hasClose = hasCloseCall(functionBody, variableName)
        if (!hasClose) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "[CHANNEL_001] Channel '$variableName' may never be closed. Use produce { } or call $variableName.close(). " +
                    "See: ${LintDocUrl.buildDocLink("71-forgetting-to-close-manual-channels")}"
            )
        }
            }
        }
    }

    private fun isInsideProduce(call: UCallExpression): Boolean {
        var current: UElement? = call
        while (current != null) {
            if (current is ULambdaExpression) {
                val parent = current.uastParent
                if (parent is UCallExpression && parent.methodName == "produce") {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }

    private fun getVariableNameFromChannelCall(call: UCallExpression): String? {
        var current: UElement? = call
        while (current != null) {
            if (current is UVariable) {
                val initializer = current.uastInitializer
                if (initializer == call || isSameExpression(initializer, call)) {
                    return current.name
                }
            }
            if (current is UBinaryExpression && current.operator.text == "=") {
                val right = current.rightOperand
                if (right == call || isSameExpression(right, call)) {
                    val left = current.leftOperand
                    if (left is UReferenceExpression) {
                        return left.asSourceString()
                    }
                }
            }
            current = current.uastParent
        }
        return null
    }

    private fun isSameExpression(a: UExpression?, b: UCallExpression): Boolean {
        if (a == null) return false
        return a.sourcePsi?.textOffset == b.sourcePsi?.textOffset
    }

    private fun findContainingFunction(call: UCallExpression): UMethod? {
        var current: UElement? = call
        while (current != null) {
            if (current is UMethod) return current
            current = current.uastParent
        }
        return null
    }

    private fun hasCloseCall(body: UExpression, variableName: String): Boolean {
        var found = false
        body.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "close") {
                    val receiverName = getReceiverSourceString(node.receiver)?.trim()?.removeSurrounding("(", ")")
                    if (receiverName == variableName) {
                        found = true
                        return false
                    }
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }

    private fun getReceiverSourceString(expr: UExpression?): String? {
        if (expr == null) return null
        return when (expr) {
            is UQualifiedReferenceExpression -> getReceiverSourceString(expr.receiver) ?: expr.receiver.asSourceString()
            is UReferenceExpression -> expr.asSourceString()
            is UParenthesizedExpression -> expr.expression?.let { getReceiverSourceString(it) }
            else -> expr.asSourceString()
        }
    }
}
