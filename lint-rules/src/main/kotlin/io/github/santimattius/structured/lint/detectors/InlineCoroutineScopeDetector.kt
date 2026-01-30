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
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects inline creation of CoroutineScope with launch/async calls.
 * Also detects property initialization with CoroutineScope(...).
 * 
 * Best Practice 1.3: Breaking Structured Concurrency by Launching in External Scopes
 * 
 * Creating a CoroutineScope inline and immediately launching a coroutine creates
 * an orphan coroutine that isn't tied to any lifecycle or structured concurrency tree.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Inline creation with launch/async
 * CoroutineScope(Dispatchers.IO).launch {
 *     fetchData()  // Orphan coroutine
 * }
 * 
 * // ❌ BAD - Property initialized with CoroutineScope
 * val viewModelScope = CoroutineScope(Dispatchers.Main)
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
class InlineCoroutineScopeDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "InlineCoroutineScope",
            briefDescription = "Inline CoroutineScope creation",
            explanation = """
                Creating a CoroutineScope inline and immediately launching a coroutine 
                creates an orphan coroutine that isn't tied to any lifecycle or 
                structured concurrency tree.
                
                Use framework scopes (viewModelScope, lifecycleScope) or structured 
                concurrency patterns (coroutineScope, supervisorScope) instead.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                InlineCoroutineScopeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("launch", "async", "CoroutineScope")
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        // Check for inline CoroutineScope creation: CoroutineScope(...).launch/async
        if (CoroutineLintUtils.isInlineCoroutineScopeCreation(node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use framework scopes (viewModelScope, lifecycleScope) or structured builders (coroutineScope { }) instead of inline CoroutineScope creation"
            )
            return
        }
        
        // Check for CoroutineScope constructor call: CoroutineScope(...)
        if (node.methodName == "CoroutineScope") {
            // Check if this is a variable initialization
            var current: UElement? = node
            while (current != null) {
                if (current is UVariable) {
                    val variableName = current.name
                    val message = if (variableName == "viewModelScope") {
                        "Don't create a custom viewModelScope. Use the official viewModelScope property from androidx.lifecycle.ViewModel"
                    } else {
                        "Use framework scopes (viewModelScope, lifecycleScope) or inject a scope annotated with @StructuredScope instead of creating CoroutineScope manually"
                    }
                    
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                    return
                }
                current = current.uastParent
            }
            
            // If not in a variable, it might be inline usage
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Use framework scopes (viewModelScope, lifecycleScope) or structured builders (coroutineScope { }) instead of creating CoroutineScope manually"
            )
        }
    }
}
