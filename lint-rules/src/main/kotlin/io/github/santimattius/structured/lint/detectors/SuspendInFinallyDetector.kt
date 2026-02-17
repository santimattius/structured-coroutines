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
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Detects suspend calls in finally blocks without withContext(NonCancellable).
 * 
 * Best Practice 4.3: Doing Suspendable Cleanup Without NonCancellable
 * 
 * In a finally block, making suspend calls without wrapping them in 
 * withContext(NonCancellable) is dangerous. If the coroutine is in *cancelling* 
 * state, any suspension will throw CancellationException again and the cleanup 
 * may not execute.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Suspend call in finally without NonCancellable
 * try {
 *     doWork()
 * } finally {
 *     saveToDb()  // May not execute if coroutine is cancelled!
 * }
 * 
 * // ✅ GOOD - Wrapped in NonCancellable
 * try {
 *     doWork()
 * } finally {
 *     withContext(NonCancellable) {
 *         saveToDb()  // Will execute even if cancelled
 *         closeResources()
 *     }
 * }
 * ```
 */
class SuspendInFinallyDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "SuspendInFinally",
            briefDescription = "Suspend call in finally without NonCancellable",
            explanation = """
                [CANCEL_004] In a finally block, making suspend calls without wrapping them in
                withContext(NonCancellable) is dangerous. If the coroutine is cancelling,
                any suspension will throw CancellationException again and the cleanup may not execute.
                Wrap critical cleanup with withContext(NonCancellable) { }.

                See: ${LintDocUrl.buildDocLink("44-cancel_004--suspendable-cleanup-without-noncancellable")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                SuspendInFinallyDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
    
    override fun visitClass(context: JavaContext, declaration: UClass) {
        declaration.accept(object : AbstractUastVisitor() {
            override fun visitTryExpression(node: UTryExpression): Boolean {
                val finallyClause = node.finallyClause ?: return super.visitTryExpression(node)

                if (finallyClause is UBlockExpression) {
                    // Check if there are suspend calls not wrapped in withContext(NonCancellable)
                    val unprotectedSuspendCalls = findUnprotectedSuspendCallsInFinally(finallyClause)
                    
                    if (unprotectedSuspendCalls.isNotEmpty()) {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node as UElement),
                            "Suspend calls in finally block should be wrapped with withContext(NonCancellable) { } to ensure cleanup executes even if cancelled"
                        )
                    }
                }
                
                return super.visitTryExpression(node)
            }
        })
    }
    
    /**
     * Finds suspend function calls in finally clause that are not wrapped in withContext(NonCancellable).
     */
    private fun findUnprotectedSuspendCallsInFinally(finallyClause: UBlockExpression): List<UCallExpression> {
        val unprotectedCalls = mutableListOf<UCallExpression>()
        var insideNonCancellable = false
        
        // Traverse the finally clause using its sourcePsi
        finallyClause.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                // Check if this is withContext(NonCancellable)
                if (isWithContextNonCancellable(node)) {
                    // Mark that we're inside NonCancellable
                    insideNonCancellable = true
                    // Don't check inside this block
                    return false
                }
                
                // Check if this might be a suspend call (heuristic: check if it's not a standard function)
                if (!insideNonCancellable && mightBeSuspendCall(node)) {
                    unprotectedCalls.add(node)
                }
                
                return super.visitCallExpression(node)
            }
        })
        
        return unprotectedCalls
    }
    
    /**
     * Checks if a call is withContext(NonCancellable).
     */
    private fun isWithContextNonCancellable(call: UCallExpression): Boolean {
        if (call.methodName != "withContext") return false
        
        val arguments = call.valueArguments
        if (arguments.isEmpty()) return false
        
        val firstArg = arguments.firstOrNull() ?: return false
        val argSource = firstArg.asSourceString()
        
        return argSource.contains("NonCancellable")
    }
    
    /**
     * Heuristic to check if a call might be a suspend function.
     * This is a simplified check - in a real implementation, you'd need to
     * check the actual function signature.
     */
    private fun mightBeSuspendCall(call: UCallExpression): Boolean {
        // Skip standard library functions that are not suspend
        val methodName = call.methodName ?: return false
        val nonSuspendFunctions = setOf(
            "println", "print", "require", "check", "error", "TODO",
            "equals", "hashCode", "toString", "compareTo"
        )
        
        if (methodName in nonSuspendFunctions) {
            return false
        }
        
        // If it's inside a suspend function context, it might be a suspend call
        // This is a heuristic - we can't always determine if a function is suspend in UAST
        return true
    }
}