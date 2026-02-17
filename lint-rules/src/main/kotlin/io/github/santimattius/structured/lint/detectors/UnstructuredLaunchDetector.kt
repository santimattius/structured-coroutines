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
import io.github.santimattius.structured.lint.utils.LintDocUrl
import org.jetbrains.uast.*

/**
 * Detects unstructured coroutine launches (launch/async without structured scope).
 * 
 * Best Practice 1.1: Using launch/async without a properly structured scope
 * 
 * This detector identifies cases where launch or async are called on scopes that
 * are not explicitly marked as structured (via @StructuredScope annotation) or
 * are not framework scopes (viewModelScope, lifecycleScope, etc.).
 * 
 * Note: This is a heuristic-based detection. GlobalScope and inline CoroutineScope
 * are already caught by GlobalScopeUsageDetector and InlineCoroutineScopeDetector.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Scope not annotated with @StructuredScope
 * fun process(scope: CoroutineScope) {
 *     scope.launch { doWork() }  // scope not marked as structured
 * }
 * 
 * // ✅ GOOD - Using @StructuredScope annotation
 * fun process(@StructuredScope scope: CoroutineScope) {
 *     scope.launch { doWork() }
 * }
 * 
 * // ✅ GOOD - Framework scopes (automatically recognized)
 * class MyViewModel : ViewModel() {
 *     fun load() {
 *         viewModelScope.launch { fetchData() }
 *     }
 * }
 * 
 * // ✅ GOOD - Structured builders
 * suspend fun process() = coroutineScope {
 *     launch { doWork() }
 * }
 * ```
 */
class UnstructuredLaunchDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "UnstructuredLaunch",
            briefDescription = "Unstructured coroutine launch",
            explanation = """
                [SCOPE_003] Launching coroutines on scopes that are not explicitly marked as structured
                makes it hard to track coroutine lifecycles and ensure proper cleanup.
                Use framework scopes (viewModelScope, lifecycleScope, rememberCoroutineScope),
                structured builders (coroutineScope, supervisorScope), or @StructuredScope.

                See: ${LintDocUrl.buildDocLink("13-scope_003--breaking-structured-concurrency")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                UnstructuredLaunchDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("launch", "async")
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        // Skip if already caught by other detectors
        if (CoroutineLintUtils.isGlobalScopeCall(node)) return
        if (CoroutineLintUtils.isInlineCoroutineScopeCreation(node)) return
        if (CoroutineLintUtils.isFrameworkScopeCall(node)) return
        
        // Check if this is inside a structured builder (coroutineScope, supervisorScope)
        if (isInsideStructuredBuilder(node)) return
        
        // Check if the receiver is annotated with @StructuredScope
        val receiver = node.receiver
        if (receiver != null && isStructuredScope(receiver)) {
            return
        }
        
        // If we get here, it's an unstructured launch
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Unstructured coroutine launch. Use framework scopes (viewModelScope, lifecycleScope), structured builders (coroutineScope { }), or annotate the scope with @StructuredScope"
        )
    }
    
    /**
     * Checks if the call is inside a structured builder (coroutineScope, supervisorScope).
     */
    private fun isInsideStructuredBuilder(call: UCallExpression): Boolean {
        var current: UElement? = call
        while (current != null) {
            if (current is UCallExpression) {
                val methodName = current.methodName
                if (methodName == "coroutineScope" || methodName == "supervisorScope") {
                    return true
                }
            }
            current = current.uastParent
        }
        return false
    }
    
    /**
     * Checks if a receiver is a structured scope (annotated with @StructuredScope).
     * This is a heuristic check based on source code analysis.
     */
    private fun isStructuredScope(receiver: UElement): Boolean {
        // Check if the receiver source contains @StructuredScope annotation
        val source = receiver.asSourceString()
        return source.contains("@StructuredScope") || 
               source.contains("StructuredScope")
    }
}
