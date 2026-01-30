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
 * Detects async calls whose Deferred result is never awaited.
 * 
 * Best Practice 1.2: Using async as if it were launch (and never calling await)
 * 
 * Using async just "to launch things" and not consuming the Deferred is confusing 
 * for code readers and can hide exceptions that remain hanging inside the Deferred.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - async without await
 * val deferred = scope.async { computeValue() }
 * // deferred never used - exception may be hidden
 * 
 * // ✅ GOOD - async with await
 * val deferred = scope.async { computeValue() }
 * val result = deferred.await()
 * 
 * // ✅ GOOD - async with awaitAll
 * val deferred1 = scope.async { computeValue1() }
 * val deferred2 = scope.async { computeValue2() }
 * val results = awaitAll(deferred1, deferred2)
 * 
 * // ✅ GOOD - Use launch if no result needed
 * scope.launch { doWork() }  // No result needed
 * ```
 */
class AsyncWithoutAwaitDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "AsyncWithoutAwait",
            briefDescription = "async without await",
            explanation = """
                Using async without calling await() is confusing and can hide exceptions 
                that remain hanging inside the Deferred. If you don't need a result, 
                use launch instead.
                
                Always call .await() or awaitAll() on Deferred values, or use launch 
                if you don't need a result.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                AsyncWithoutAwaitDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        
        private val ASYNC_NAME = "async"
        private val AWAIT_NAME = "await"
        private val AWAIT_ALL_NAME = "awaitAll"
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return listOf(ASYNC_NAME)
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        if (node.methodName != ASYNC_NAME) return
        
        // Find the containing function/block
        val containingBlock = findContainingBlock(node) ?: return
        
        // Check if this async is part of a variable assignment
        val variableName = findVariableNameFromAsyncCall(node, containingBlock) ?: return
        
        // Check if await() is called on this variable in the same block
        if (!hasAwaitCall(containingBlock, variableName)) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "async result is never awaited. Call .await() or awaitAll() on the Deferred, or use launch if no result is needed"
            )
        }
    }
    
    /**
     * Finds the containing block (function body) for the async call.
     */
    private fun findContainingBlock(call: UCallExpression): UExpression? {
        var current: UElement? = call
        while (current != null) {
            // UMethod represents both Java methods and Kotlin functions
            if (current is UMethod) {
                return current.uastBody
            }
            if (current is UBlockExpression) {
                return current
            }
            current = current.uastParent
        }
        return null
    }
    
    /**
     * Finds the variable name from an async call assignment.
     */
    private fun findVariableNameFromAsyncCall(
        asyncCall: UCallExpression,
        block: UExpression
    ): String? {
        // Check if the async call is directly assigned to a variable
        var current: UElement? = asyncCall
        while (current != null) {
            // Check for variable declaration: val x = async { }
            if (current is UVariable) {
                val initializer = current.uastInitializer
                if (initializer == asyncCall || 
                    (initializer is UCallExpression && 
                     initializer.sourcePsi == asyncCall.sourcePsi)) {
                    return current.name
                }
            }
            
            // Check for binary assignment: x = async { }
            if (current is UBinaryExpression) {
                val operatorText = current.operator.text
                if (operatorText == "=") {
                    val right = current.rightOperand
                    if (right == asyncCall || right.sourcePsi == asyncCall.sourcePsi) {
                        val left = current.leftOperand
                        if (left is UReferenceExpression) {
                            return left.asSourceString()
                        }
                    }
                }
            }
            
            current = current.uastParent
        }
        
        return null
    }
    
    /**
     * Checks if await() or awaitAll() is called on the given variable in the block.
     */
    private fun hasAwaitCall(block: UExpression, variableName: String): Boolean {
        var foundAwait = false
        
        block.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName
                
                // Check for .await() call
                if (methodName == AWAIT_NAME || methodName == AWAIT_ALL_NAME) {
                    val receiver = node.receiver
                    if (receiver is UQualifiedReferenceExpression) {
                        val receiverName = receiver.receiver.asSourceString()
                        if (receiverName == variableName) {
                            foundAwait = true
                            return false // Stop traversal
                        }
                    }
                }
                
                // Check for awaitAll(deferred1, deferred2, ...)
                if (methodName == AWAIT_ALL_NAME) {
                    val arguments = node.valueArguments
                    for (arg in arguments) {
                        val argSource = arg.asSourceString()
                        if (argSource == variableName || argSource.contains(variableName)) {
                            foundAwait = true
                            return false // Stop traversal
                        }
                    }
                }
                
                return super.visitCallExpression(node)
            }
        })
        
        return foundAwait
    }
}
