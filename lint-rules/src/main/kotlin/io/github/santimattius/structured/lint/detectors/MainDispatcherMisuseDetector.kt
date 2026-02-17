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
import io.github.santimattius.structured.lint.utils.CoroutineLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects blocking code on Dispatchers.Main.
 * 
 * Best Practice 3.1: Mixing Blocking Code with Dispatchers.Default or Dispatchers.Main
 * 
 * Doing blocking I/O (files, JDBC, synchronous libraries) in coroutines running 
 * on Dispatchers.Main can freeze the UI and cause ANRs (Application Not Responding).
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Blocking call on Main dispatcher
 * viewModelScope.launch(Dispatchers.Main) {
 *     Thread.sleep(1000)  // Freezes UI!
 *     inputStream.read()  // Blocks Main thread
 *     jdbcStatement.executeQuery()  // Blocks Main thread
 * }
 * 
 * // ✅ GOOD - Use Dispatchers.IO for blocking operations
 * viewModelScope.launch(Dispatchers.Main) {
 *     updateUI()  // Quick UI update
 *     withContext(Dispatchers.IO) {
 *         inputStream.read()  // Blocking I/O on IO dispatcher
 *     }
 * }
 * 
 * // ✅ GOOD - Use appropriate dispatcher from the start
 * viewModelScope.launch(Dispatchers.IO) {
 *     inputStream.read()
 *     withContext(Dispatchers.Main) {
 *         updateUI()  // Switch back to Main for UI update
 *     }
 * }
 * ```
 */
class MainDispatcherMisuseDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "MainDispatcherMisuse",
            briefDescription = "Blocking code on Main dispatcher",
            explanation = """
                [DISPATCH_001] Blocking operations on Dispatchers.Main can freeze the UI and cause
                ANRs (Application Not Responding).

                For blocking operations (I/O, JDBC, synchronous HTTP), use
                Dispatchers.IO or wrap the blocking code with withContext(Dispatchers.IO).

                Dispatchers.Main should only be used for quick UI updates and
                non-blocking operations.

                See: ${LintDocUrl.buildDocLink("31-dispatch_001--mixing-blocking-code-with-wrong-dispatchers")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 10, // Highest priority - can cause ANRs
            severity = Severity.ERROR,
            implementation = Implementation(
                MainDispatcherMisuseDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("launch", "async", "withContext")
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        // Check if this call uses Dispatchers.Main
        if (!CoroutineLintUtils.usesMainDispatcher(node)) {
            return
        }
        
        // Find the lambda body of the coroutine builder
        val lambdaBody = getLambdaBody(node) ?: return
        
        // Check if the lambda body contains blocking calls
        val blockingCalls = findBlockingCallsInLambda(lambdaBody)
        
        if (blockingCalls.isNotEmpty()) {
            for (blockingCall in blockingCalls) {
                context.report(
                    ISSUE,
                    blockingCall,
                    context.getLocation(blockingCall),
                    "Blocking call on Main dispatcher. Move this to Dispatchers.IO using withContext(Dispatchers.IO) { }"
                )
            }
        }
    }
    
    /**
     * Gets the lambda body from a coroutine builder call.
     */
    private fun getLambdaBody(call: UCallExpression): UExpression? {
        val arguments = call.valueArguments
        if (arguments.isEmpty()) return null
        
        // The last argument is typically the lambda
        val lastArg = arguments.lastOrNull() ?: return null
        
        return when (lastArg) {
            is ULambdaExpression -> lastArg.body
            is UBlockExpression -> lastArg
            else -> null
        }
    }
    
    /**
     * Finds all blocking calls within a lambda expression.
     */
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
