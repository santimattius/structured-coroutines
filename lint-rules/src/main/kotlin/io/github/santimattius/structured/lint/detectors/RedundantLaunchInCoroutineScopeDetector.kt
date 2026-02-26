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
 * Detects redundant launch calls inside coroutineScope.
 * 
 * Best Practice 2.1: Using launch on the Last Line of a coroutineScope
 * 
 * Using launch on the last line of a coroutineScope is redundant because
 * coroutineScope already waits for all children to complete. If you want the
 * function to wait, execute the work directly without wrapping it in launch.
 * 
 * Example:
 * ```kotlin
 * // ⚠️ WARNING - Redundant launch
 * suspend fun bad() = coroutineScope {
 *     launch { work() }  // Innecesario - debería ser solo work()
 * }
 * 
 * // ✅ GOOD - Direct execution
 * suspend fun good() = coroutineScope {
 *     work()  // Directo, sin launch
 * }
 * 
 * // ✅ GOOD - Multiple launches (not redundant)
 * suspend fun good() = coroutineScope {
 *     launch { work1() }
 *     launch { work2() }
 * }
 * ```
 */
class RedundantLaunchInCoroutineScopeDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "RedundantLaunchInCoroutineScope",
            briefDescription = "Redundant launch in coroutineScope",
            explanation = """
                [RUNBLOCK_001] Using launch on the last line of a coroutineScope is redundant because
                coroutineScope already waits for all children to complete. If you want
                the function to wait, execute the work directly without wrapping it in launch.

                See: ${LintDocUrl.buildDocLink("21-runblock_001--using-launch-on-the-last-line-of-coroutinescope")}
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                RedundantLaunchInCoroutineScopeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("coroutineScope")
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        if (node.methodName != "coroutineScope") return
        
        // Get the lambda body
        val lambdaBody = getLambdaBody(node) ?: return

        // Count and find launch/async calls in the block
        val builderCalls = collectBuilderCalls(lambdaBody)
        val launchCalls = builderCalls.filter { it.methodName == "launch" }
        val asyncCalls = builderCalls.filter { it.methodName == "async" }
        val launchCount = launchCalls.size
        val totalBuilders = builderCalls.size

        // If there's exactly 1 launch and no other builders, report unless inside forEach/for/while
        if (launchCount == 1 && totalBuilders == 1) {
            val singleLaunch = launchCalls.single()
            if (isInsideRepeatingContext(singleLaunch, lambdaBody)) return

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Redundant launch in coroutineScope. If you want the function to wait, execute the work directly without wrapping it in launch"
            )
        }
    }
    
    /**
     * Gets the lambda body from the coroutineScope call.
     */
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
    
    /**
     * Collects all launch and async calls in an expression.
     */
    private fun collectBuilderCalls(expression: UExpression): List<UCallExpression> {
        val calls = mutableListOf<UCallExpression>()
        expression.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "launch" || node.methodName == "async") {
                    calls.add(node)
                }
                return super.visitCallExpression(node)
            }
        })
        return calls
    }

    /**
     * True when the launch is inside forEach/for/while so it runs multiple times; scope correctly waits for all.
     */
    private fun isInsideRepeatingContext(builderCall: UCallExpression, scopeBody: UExpression): Boolean {
        val iterationMethods = setOf("forEach", "onEach", "map", "mapNotNull", "flatMap", "filter", "filterNotNull")
        var p: UElement? = builderCall
        while (p != null && p != scopeBody) {
            when (p) {
                is ULambdaExpression -> {
                    var ancestor: UElement? = p.uastParent
                    while (ancestor != null && ancestor != scopeBody) {
                        if (ancestor is UCallExpression && ancestor.methodName in iterationMethods) return true
                        ancestor = ancestor.uastParent
                    }
                }
                is UForExpression, is UWhileExpression -> return true
                else -> {}
            }
            p = p.uastParent
        }
        return false
    }
}
