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
 * Detects potential ViewModel scope leaks and incorrect usage.
 * 
 * Android-specific: Detects cases where viewModelScope is used incorrectly
 * or where custom scopes are created in ViewModels instead of using the
 * official viewModelScope.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Custom scope in ViewModel
 * class MyViewModel : ViewModel() {
 *     private val customScope = CoroutineScope(Dispatchers.Main)
 *     
 *     fun load() {
 *         customScope.launch { fetchData() }  // Not lifecycle-aware
 *     }
 * }
 * 
 * // ✅ GOOD - Use official viewModelScope
 * class MyViewModel : ViewModel() {
 *     fun load() {
 *         viewModelScope.launch { fetchData() }  // Lifecycle-aware
 *     }
 * }
 * 
 * // ❌ BAD - viewModelScope used outside ViewModel
 * class MyRepository {
 *     fun load(viewModel: MyViewModel) {
 *         viewModel.viewModelScope.launch { }  // Wrong context
 *     }
 * }
 * 
 * // ✅ GOOD - Inject scope or use structured concurrency
 * class MyRepository(private val scope: CoroutineScope) {
 *     fun load() {
 *         scope.launch { fetchData() }
 *     }
 * }
 * ```
 */
class ViewModelScopeLeakDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "ViewModelScopeLeak",
            briefDescription = "Incorrect ViewModel scope usage",
            explanation = """
                [SCOPE_003] ViewModels should use the official viewModelScope which is automatically
                cancelled when the ViewModel is cleared. Creating custom scopes in ViewModels
                or using viewModelScope outside of ViewModels can lead to memory leaks.

                Always use viewModelScope.launch { } inside ViewModel classes.
                For other classes, inject a CoroutineScope or use structured concurrency.

                See: ${LintDocUrl.buildDocLink("13-scope_003--breaking-structured-concurrency")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                ViewModelScopeLeakDetector::class.java,
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
        // Check for CoroutineScope constructor call in ViewModel
        if (node.methodName == "CoroutineScope" && AndroidLintUtils.isInViewModel(context, node)) {
            // Check if this is a variable initialization
            var current: UElement? = node
            while (current != null) {
                if (current is UVariable) {
                    val variableName = current.name
                    val message = if (variableName == "viewModelScope") {
                        "[SCOPE_003] Don't create a custom viewModelScope. Use the official viewModelScope property from androidx.lifecycle.ViewModel. " +
                            "See: ${LintDocUrl.buildDocLink("13-scope_003--breaking-structured-concurrency")}"
                    } else {
                        "[SCOPE_003] Don't create custom CoroutineScope in ViewModel. Use viewModelScope instead. " +
                            "See: ${LintDocUrl.buildDocLink("13-scope_003--breaking-structured-concurrency")}"
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
        }
        
        // Check if this is a call on a custom scope in a ViewModel
        if (isCustomScopeInViewModel(context, node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "[SCOPE_003] Use viewModelScope instead of creating a custom CoroutineScope in ViewModel. " +
                    "See: ${LintDocUrl.buildDocLink("13-scope_003--breaking-structured-concurrency")}"
            )
        }
        
        // Check if viewModelScope is used outside of a ViewModel
        if (isViewModelScopeUsedOutsideViewModel(context, node)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "[SCOPE_003] viewModelScope should only be used inside ViewModel classes. Use injected scope or structured concurrency instead. " +
                    "See: ${LintDocUrl.buildDocLink("13-scope_003--breaking-structured-concurrency")}"
            )
        }
    }
    
    /**
     * Checks if a custom CoroutineScope is being used in a ViewModel.
     */
    private fun isCustomScopeInViewModel(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        // Check if we're inside a ViewModel
        if (!AndroidLintUtils.isInViewModel(context, call)) {
            return false
        }
        
        // Check if the receiver is a custom scope (not viewModelScope)
        val receiver = call.receiver as? UQualifiedReferenceExpression
        val receiverName = receiver?.receiver?.asSourceString() ?: return false
        
        // If it's viewModelScope, it's fine
        if (receiverName == "viewModelScope") {
            return false
        }
        
        // If it's a property access (like customScope.launch), it might be a custom scope
        // Check if it's a variable that was initialized with CoroutineScope
        return receiverName != "lifecycleScope" && 
               receiverName != "GlobalScope" &&
               !CoroutineLintUtils.isFrameworkScopeCall(call)
    }
    
    /**
     * Checks if viewModelScope is used outside of a ViewModel class.
     */
    private fun isViewModelScopeUsedOutsideViewModel(
        context: JavaContext,
        call: UCallExpression
    ): Boolean {
        val receiver = call.receiver as? UQualifiedReferenceExpression
        val receiverName = receiver?.receiver?.asSourceString() ?: return false
        
        // Check if this is a viewModelScope call
        if (receiverName != "viewModelScope") {
            return false
        }
        
        // Check if we're NOT inside a ViewModel
        return !AndroidLintUtils.isInViewModel(context, call)
    }
    
}
