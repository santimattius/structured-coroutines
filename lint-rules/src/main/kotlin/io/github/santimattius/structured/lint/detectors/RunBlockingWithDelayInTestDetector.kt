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
 * Detects runBlocking with delay in test files.
 * 
 * Best Practice 6.1: Slow Tests Using runBlocking + Real delay
 * 
 * Using runBlocking with delay() in tests makes tests slow and flaky because
 * they wait for real time to pass. This defeats the purpose of virtual time
 * testing provided by kotlinx-coroutines-test.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Slow test with real delays
 * @Test
 * fun `test something`() = runBlocking {
 *     delay(1000)  // Waits 1 real second
 *     val result = repository.getData()
 *     assertEquals(expected, result)
 * }
 * 
 * // ✅ GOOD - Fast test with virtual time
 * @Test
 * fun `test something`() = runTest {
 *     delay(1000)  // Instant - uses virtual time
 *     val result = repository.getData()
 *     assertEquals(expected, result)
 * }
 * ```
 */
class RunBlockingWithDelayInTestDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "RunBlockingWithDelayInTest",
            briefDescription = "runBlocking with delay in test file",
            explanation = """
                [TEST_001] Using runBlocking with delay() in tests makes tests slow and flaky because
                they wait for real time to pass. This defeats the purpose of virtual time
                testing provided by kotlinx-coroutines-test.

                Use runTest { } from kotlinx-coroutines-test for instant virtual time support.

                See: ${LintDocUrl.buildDocLink("61-test_001--slow-tests-with-real-delays")}
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                RunBlockingWithDelayInTestDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("runBlocking")
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        // Only check in test files
        if (!AndroidLintUtils.isTestFile(context, node)) return
        
        if (node.methodName != "runBlocking") return
        
        // Get the lambda body
        val lambdaBody = getLambdaBody(node) ?: return
        
        // Check if the lambda contains delay()
        if (containsDelayCall(lambdaBody)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "[TEST_001] runBlocking with delay() in test file. Use runTest { } from kotlinx-coroutines-test for instant virtual time. " +
                    "See: ${LintDocUrl.buildDocLink("61-test_001--slow-tests-with-real-delays")}"
            )
        }
    }
    
    /**
     * Gets the lambda body from the runBlocking call.
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
     * Checks if the expression contains any delay() calls.
     */
    private fun containsDelayCall(expression: UExpression): Boolean {
        var foundDelay = false
        
        expression.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "delay") {
                    foundDelay = true
                    return false // Stop traversal
                }
                return super.visitCallExpression(node)
            }
        })
        
        return foundDelay
    }
}
