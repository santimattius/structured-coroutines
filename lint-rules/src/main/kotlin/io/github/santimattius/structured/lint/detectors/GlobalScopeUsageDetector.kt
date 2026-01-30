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
 * Detects usage of GlobalScope.launch or GlobalScope.async.
 * 
 * Best Practice 1.1: Using GlobalScope in Production Code
 * 
 * GlobalScope breaks structured concurrency by creating orphan coroutines
 * that don't have a parent-child relationship, making cancellation and
 * exception propagation difficult to manage.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD
 * GlobalScope.launch { fetchData() }
 * 
 * // ✅ GOOD - Framework scopes
 * class MyViewModel : ViewModel() {
 *     fun load() {
 *         viewModelScope.launch { fetchData() }
 *     }
 * }
 * 
 * // ✅ GOOD - Structured builders
 * suspend fun process() = coroutineScope {
 *     launch { fetchData() }
 * }
 * ```
 */
class GlobalScopeUsageDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "GlobalScopeUsage",
            briefDescription = "GlobalScope usage in production code",
            explanation = """
                GlobalScope breaks structured concurrency by creating orphan coroutines 
                that don't have a parent-child relationship. This makes cancellation 
                and exception propagation difficult to manage.
                
                Use framework scopes (viewModelScope, lifecycleScope, rememberCoroutineScope) 
                or structured concurrency patterns (coroutineScope, supervisorScope) instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                GlobalScopeUsageDetector::class.java,
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
        if (CoroutineLintUtils.isGlobalScopeCall(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use viewModelScope, lifecycleScope, rememberCoroutineScope(), or coroutineScope { } instead of GlobalScope"
            )
        }
    }
}
