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
 * Detects Flow collection in Activity/Fragment with lifecycleScope without
 * repeatOnLifecycle or flowWithLifecycle (Best Practice 8.2 ARCH_002).
 *
 * Collecting a Flow in lifecycleScope.launch { flow.collect { } } keeps the flow
 * running when the UI goes to background. Use repeatOnLifecycle(Lifecycle.State.STARTED)
 * or flowWithLifecycle so collection cancels when the lifecycle stops.
 */
class LifecycleAwareFlowCollectionDetector : Detector(), SourceCodeScanner {

    companion object {
        val ISSUE = Issue.create(
            id = "LifecycleAwareFlowCollection",
            briefDescription = "Flow collection in lifecycleScope without repeatOnLifecycle",
            explanation = """
                [ARCH_002] Collecting a Flow in lifecycleScope.launch { flow.collect { } } without
                repeatOnLifecycle or flowWithLifecycle keeps the flow running when the UI goes to
                background. Use repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { } } or
                flowWithLifecycle(lifecycle, Lifecycle.State.STARTED) so collection cancels when
                the lifecycle stops.

                See: ${LintDocUrl.buildDocLink("82-lifecycle-aware-flow-collection-android")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                LifecycleAwareFlowCollectionDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )

        private val LAUNCH_METHODS = setOf("launch", "launchWhenStarted", "launchWhenCreated", "launchWhenResumed")
        private val COLLECT_METHODS = setOf("collect", "collectLatest", "collectIndexed")
        private val LIFECYCLE_SAFE_METHODS = setOf("repeatOnLifecycle", "flowWithLifecycle")
    }

    override fun getApplicableMethodNames(): List<String> = LAUNCH_METHODS.toList()

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        if (node.methodName !in LAUNCH_METHODS) return

        val receiverName = getReceiverScopeName(node) ?: return
        if (receiverName != "lifecycleScope" && !receiverName.endsWith(".lifecycleScope")) return

        if (!AndroidLintUtils.isInLifecycleOwner(context, node)) return

        val lambdaBody = getLambdaBody(node) ?: return
        if (!containsFlowCollect(lambdaBody)) return
        if (containsRepeatOnLifecycleOrFlowWithLifecycle(lambdaBody)) return

        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "[ARCH_002] Flow collection in lifecycleScope without repeatOnLifecycle. " +
                "Use repeatOnLifecycle(Lifecycle.State.STARTED) { flow.collect { } } or flowWithLifecycle so collection stops when UI is in background. " +
                "See: ${LintDocUrl.buildDocLink("82-lifecycle-aware-flow-collection-android")}"
        )
    }

    private fun getReceiverScopeName(call: UCallExpression): String? {
        val receiver = call.receiver ?: return null
        return when (receiver) {
            is UReferenceExpression -> receiver.asSourceString()
            is UQualifiedReferenceExpression -> receiver.selector?.asSourceString()
            else -> null
        }
    }

    private fun getLambdaBody(call: UCallExpression): UExpression? {
        val args = call.valueArguments
        if (args.isEmpty()) return null
        val last = args.lastOrNull() ?: return null
        return when (last) {
            is ULambdaExpression -> last.body
            is UBlockExpression -> last
            else -> null
        }
    }

    private fun containsFlowCollect(body: UExpression): Boolean {
        var found = false
        body.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName in COLLECT_METHODS) {
                    found = true
                    return false
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }

    private fun containsRepeatOnLifecycleOrFlowWithLifecycle(body: UExpression): Boolean {
        var found = false
        body.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName in LIFECYCLE_SAFE_METHODS) {
                    found = true
                    return false
                }
                return super.visitCallExpression(node)
            }
        })
        return found
    }
}
