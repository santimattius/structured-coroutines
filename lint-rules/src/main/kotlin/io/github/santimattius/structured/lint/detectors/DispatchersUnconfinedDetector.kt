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
import io.github.santimattius.structured.lint.utils.CoroutineLintUtils
import org.jetbrains.uast.UCallExpression

/**
 * Detects usage of Dispatchers.Unconfined in coroutine builders.
 * 
 * Best Practice 3.2: Abusing Dispatchers.Unconfined
 * 
 * Dispatchers.Unconfined runs code on whatever thread happens to resume it,
 * which makes it difficult to reason about execution and can end up running
 * on the UI thread with blocking calls.
 * 
 * Example:
 * ```kotlin
 * // ⚠️ WARNING - Dispatchers.Unconfined
 * scope.launch(Dispatchers.Unconfined) {
 *     doWork()  // Thread impredecible
 * }
 * 
 * // ✅ GOOD - Dispatchers apropiados
 * scope.launch(Dispatchers.Default) {  // CPU-bound
 *     heavyComputation()
 * }
 * 
 * scope.launch(Dispatchers.IO) {  // IO-bound
 *     networkCall()
 * }
 * 
 * scope.launch(Dispatchers.Main) {  // UI updates
 *     updateUI()
 * }
 * ```
 */
class DispatchersUnconfinedDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "DispatchersUnconfined",
            briefDescription = "Dispatchers.Unconfined usage",
            explanation = """
                Dispatchers.Unconfined runs code on whatever thread happens to resume it, 
                which makes it difficult to reason about execution and can end up running 
                on the UI thread with blocking calls.
                
                In production, always choose a dispatcher appropriate for the type of 
                workload: Default (CPU-bound), Main (UI), IO (blocking I/O), etc.
                
                Reserve Dispatchers.Unconfined for very special cases or legacy testing.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                DispatchersUnconfinedDetector::class.java,
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
        if (CoroutineLintUtils.usesUnconfinedDispatcher(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use an appropriate dispatcher (Dispatchers.Default, Dispatchers.IO, Dispatchers.Main) instead of Dispatchers.Unconfined"
            )
        }
    }
}
