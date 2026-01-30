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
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects obvious cases of scope cancellation followed by reuse.
 * 
 * Best Practice 4.4: Cancelling a CoroutineScope and Continuing to Reuse It (Parcial)
 * 
 * Doing scope.cancel() and then trying to launch more coroutines in that same scope
 * doesn't work. A scope with a cancelled Job doesn't accept new children, and 
 * subsequent launches fail silently.
 * 
 * Note: This is a partial/heuristic detection. It only detects obvious cases where
 * cancel() is called on a scope variable followed by launch/async on the same variable
 * in the same function. Complex cases requiring flow analysis are not detected.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Scope cancelled and then reused
 * fun process(scope: CoroutineScope) {
 *     scope.cancel()
 *     scope.launch { work() }  // Won't work - scope is cancelled
 * }
 * 
 * // ✅ GOOD - Use cancelChildren() instead
 * fun process(scope: CoroutineScope) {
 *     scope.coroutineContext.job.cancelChildren()  // Cancel children, keep scope usable
 *     scope.launch { work() }  // Still works
 * }
 * 
 * // ✅ GOOD - Only cancel when scope won't be reused
 * fun cleanup(scope: CoroutineScope) {
 *     scope.cancel()  // OK - scope won't be used again
 * }
 * ```
 */
class ScopeReuseAfterCancelDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "ScopeReuseAfterCancel",
            briefDescription = "Scope cancelled and then reused",
            explanation = """
                Cancelling a CoroutineScope and then trying to launch more coroutines 
                in that same scope doesn't work. A scope with a cancelled Job doesn't 
                accept new children, and subsequent launches fail silently.
                
                To "clean up" children but keep the scope usable, use 
                coroutineContext.job.cancelChildren() instead of scope.cancel().
                
                Note: This is a heuristic detection. It only detects obvious cases in 
                the same function. Complex cases requiring flow analysis are not detected.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ScopeReuseAfterCancelDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf("cancel", "launch", "async")
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        // Find the containing function
        val containingFunction = findContainingFunction(node) ?: return
        
        // If this is a cancel() call, check if scope is reused later
        if (node.methodName == "cancel") {
            val scopeName = getScopeNameFromCancelCall(node) ?: return
            
            // Check if the same scope is used in launch/async later in the function
            if (isScopeUsedLater(containingFunction, scopeName, node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Scope '$scopeName' is cancelled and then reused. Use cancelChildren() instead of cancel() if you need to reuse the scope"
                )
            }
        }
    }
    
    /**
     * Finds the containing function for a call expression.
     */
    private fun findContainingFunction(call: UCallExpression): UMethod? {
        var current: UElement? = call
        while (current != null) {
            if (current is UMethod) {
                return current
            }
            current = current.uastParent
        }
        return null
    }
    
    /**
     * Gets the scope name from a cancel() call (e.g., "scope" from "scope.cancel()").
     */
    private fun getScopeNameFromCancelCall(call: UCallExpression): String? {
        val receiver = call.receiver
        return when (receiver) {
            is UQualifiedReferenceExpression -> receiver.receiver.asSourceString()
            is UReferenceExpression -> receiver.asSourceString()
            else -> null
        }
    }
    
    /**
     * Checks if a scope is used in launch/async calls after the cancel() call.
     */
    private fun isScopeUsedLater(
        function: UMethod,
        scopeName: String,
        cancelCall: UCallExpression
    ): Boolean {
        val functionBody = function.uastBody ?: return false
        val cancelCallOffset = cancelCall.sourcePsi?.textOffset ?: return false
        
        var foundReuse = false
        
        functionBody.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                // Only check calls after the cancel() call
                val nodeOffset = node.sourcePsi?.textOffset ?: return true
                if (nodeOffset <= cancelCallOffset) return true
                
                // Check if this is a launch/async call
                if (node.methodName == "launch" || node.methodName == "async") {
                    // Check if it's called on the same scope
                    val receiver = node.receiver
                    val receiverName = when (receiver) {
                        is UQualifiedReferenceExpression -> receiver.receiver.asSourceString()
                        is UReferenceExpression -> receiver.asSourceString()
                        else -> null
                    }
                    
                    if (receiverName == scopeName) {
                        foundReuse = true
                        return false // Stop traversal
                    }
                }
                
                return super.visitCallExpression(node)
            }
        })
        
        return foundReuse
    }
}
