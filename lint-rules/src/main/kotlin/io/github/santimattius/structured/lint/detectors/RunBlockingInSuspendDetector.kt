/**
 * Copyright 2024 Santiago Mattiauda
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
import io.github.santimattius.structured.lint.utils.CoroutineLintUtils
import org.jetbrains.uast.UCallExpression

/**
 * Detects runBlocking calls inside suspend functions.
 * 
 * Best Practice 2.2: Using runBlocking Inside suspend Functions
 * 
 * runBlocking blocks the current thread, which breaks the non-blocking model
 * of coroutines and can cause deadlocks or ANRs (on Android).
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - runBlocking en suspend function
 * suspend fun fetchData() {
 *     runBlocking {  // Bloquea el thread!
 *         delay(1000)
 *         loadFromNetwork()
 *     }
 * }
 * 
 * // ✅ GOOD - Direct suspend
 * suspend fun fetchData() {
 *     delay(1000)  // Non-blocking
 *     loadFromNetwork()
 * }
 * 
 * // ✅ GOOD - runBlocking en top level (entry point)
 * fun main() = runBlocking {
 *     fetchData()
 * }
 * ```
 */
class RunBlockingInSuspendDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "RunBlockingInSuspend",
            briefDescription = "runBlocking in suspend function",
            explanation = """
                runBlocking blocks the current thread, which breaks the non-blocking model 
                of coroutines and can cause deadlocks or ANRs (on Android).
                
                Inside suspend functions, keep suspending. Use the suspend versions of 
                libraries or wrap blocking operations with withContext(Dispatchers.IO) 
                when no suspend API is available.
                
                runBlocking should only be used as a bridge from purely blocking code 
                to coroutines (e.g., old entry points, console scripts).
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                RunBlockingInSuspendDetector::class.java,
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
        if (CoroutineLintUtils.isRunBlockingCall(node) &&
            CoroutineLintUtils.isInSuspendFunction(context, node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Remove runBlocking wrapper. Inside suspend functions, use suspend calls directly or withContext(Dispatchers.IO) for blocking operations"
            )
        }
    }
}
