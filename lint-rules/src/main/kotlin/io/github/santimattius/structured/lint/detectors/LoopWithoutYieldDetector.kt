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
 * Detects loops without cooperation points in suspend functions.
 * 
 * Best Practice 4.1: Ignoring Cancellation in Intensive Loops (Parcial)
 * 
 * Long-running loops in suspend functions without cooperation points (yield, 
 * ensureActive, delay) cannot be cancelled until the loop completes. This defeats 
 * the cooperative nature of coroutine cancellation.
 * 
 * Note: This is a heuristic rule. It flags loops that don't contain any known 
 * cooperation points. False positives may occur if the loop body contains suspend 
 * function calls that themselves provide cooperation points.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Loop cannot be cancelled
 * suspend fun processItems(items: List<Item>) {
 *     for (item in items) {
 *         heavyComputation(item)  // No cooperation point
 *     }
 * }
 * 
 * // ✅ GOOD - Loop can be cancelled
 * suspend fun processItems(items: List<Item>) {
 *     for (item in items) {
 *         ensureActive()  // Check for cancellation
 *         heavyComputation(item)
 *     }
 * }
 * 
 * // ✅ GOOD - Using yield for CPU-bound work
 * suspend fun processItems(items: List<Item>) {
 *     for (item in items) {
 *         yield()  // Cooperation point
 *         heavyComputation(item)
 *     }
 * }
 * ```
 */
class LoopWithoutYieldDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "LoopWithoutYield",
            briefDescription = "Loop without cooperation point in suspend function",
            explanation = """
                [CANCEL_001] Long-running loops in suspend functions without cooperation points
                (yield, ensureActive, delay) cannot be cancelled until the loop completes.

                - Inside a scope builder (launch/async/coroutineScope/supervisorScope/withContext): use ensureActive().
                - Only inside a suspend function (no scope builder block): use currentCoroutineContext().ensureActive(), yield(), or delay().

                Note: This is a heuristic rule. False positives may occur if the loop body
                contains suspend function calls that themselves provide cooperation points.

                See: ${LintDocUrl.buildDocLink("41-cancel_001--ignoring-cancellation-in-intensive-loops")}
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                LoopWithoutYieldDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        
        private val COOPERATION_POINTS = setOf(
            "yield",
            "ensureActive",
            "delay",
            "suspendCancellableCoroutine",
            "withTimeout",
            "withTimeoutOrNull"
        )
    }
    
    override fun visitClass(context: JavaContext, declaration: UClass) {
        declaration.accept(object : AbstractUastVisitor() {
            override fun visitForExpression(node: UForExpression): Boolean {
                checkLoop(context, node, node.body)
                return super.visitForExpression(node)
            }
            
            override fun visitWhileExpression(node: UWhileExpression): Boolean {
                checkLoop(context, node, node.body)
                return super.visitWhileExpression(node)
            }
        })
    }
    
    private fun checkLoop(
        context: JavaContext,
        loop: ULoopExpression,
        loopBody: UExpression?
    ) {
        // Only check inside suspend functions
        if (!CoroutineLintUtils.isInSuspendFunction(context, loop)) {
            return
        }
        
        if (loopBody == null) return
        
        // Check if the loop body contains any cooperation points
        val hasCooperationPoint = hasCooperationPoint(loopBody)
        
        if (!hasCooperationPoint) {
            val loopType = when (loop) {
                is UForExpression -> "for"
                is UWhileExpression -> "while"
                else -> "loop"
            }
            val insideScope = CoroutineLintUtils.isInsideScopeBuilderBlock(loop)
            val suggestion = if (insideScope) {
                "Add ensureActive(), yield(), or delay(0) inside the loop to enable cancellation"
            } else {
                "Add currentCoroutineContext().ensureActive(), yield(), or delay(0) inside the loop to enable cancellation"
            }
            context.report(
                ISSUE,
                loop,
                context.getLocation(loop),
                "'$loopType' loop in suspend function without cooperation point. $suggestion"
            )
        }
    }
    
    /**
     * Checks if an expression contains any cooperation points.
     */
    private fun hasCooperationPoint(expression: UExpression): Boolean {
        var found = false
        
        expression.accept(object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName
                if (methodName in COOPERATION_POINTS) {
                    found = true
                    return false // Stop traversal
                }
                return super.visitCallExpression(node)
            }
        })
        
        return found
    }
}
