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
 * Detects catch(Exception) blocks in suspend functions that may swallow CancellationException.
 * 
 * Best Practice 4.2: Consuming CancellationException as if it Were a Normal Error
 * 
 * Doing catch (e: Exception) and treating CancellationException the same as any 
 * other error can prevent cancellation from propagating correctly and leave 
 * coroutines alive when they should terminate.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Swallows CancellationException
 * suspend fun bad() {
 *     try {
 *         work()
 *     } catch (e: Exception) {
 *         log(e)  // CancellationException is caught and not re-thrown!
 *     }
 * }
 * 
 * // ✅ GOOD - Explicit CancellationException handling
 * suspend fun good() {
 *     try {
 *         work()
 *     } catch (e: CancellationException) {
 *         throw e  // Always re-throw!
 *     } catch (e: Exception) {
 *         log(e)
 *     }
 * }
 * 
 * // ✅ GOOD - Using ensureActive() in catch block
 * suspend fun alsoGood() {
 *     try {
 *         work()
 *     } catch (e: Exception) {
 *         ensureActive()  // Re-throws if cancelled
 *         log(e)
 *     }
 * }
 * ```
 */
class CancellationExceptionSwallowedDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "CancellationExceptionSwallowed",
            briefDescription = "catch(Exception) may swallow CancellationException",
            explanation = """
                Catching Exception or Throwable in suspend functions can prevent 
                CancellationException from propagating correctly, leaving coroutines 
                alive when they should terminate.
                
                Always handle CancellationException separately by either:
                1. Adding a catch (e: CancellationException) { throw e } clause before 
                   the general Exception catch
                2. Calling ensureActive() in the catch block to re-throw if cancelled
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                CancellationExceptionSwallowedDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        
        private val BROAD_EXCEPTION_TYPES = setOf(
            "Exception",
            "Throwable",
            "java.lang.Exception",
            "java.lang.Throwable"
        )
        
        private val CANCELLATION_EXCEPTION_TYPES = setOf(
            "CancellationException",
            "kotlinx.coroutines.CancellationException",
            "java.util.concurrent.CancellationException"
        )
    }
    
    override fun visitClass(context: JavaContext, declaration: UClass) {
        declaration.accept(object : AbstractUastVisitor() {
            override fun visitTryExpression(node: UTryExpression): Boolean {
                // Only check inside suspend functions
                if (!CoroutineLintUtils.isInSuspendFunction(context, node)) {
                    return super.visitTryExpression(node)
                }
                
                val catchClauses = node.catchClauses
                if (catchClauses.isEmpty()) return super.visitTryExpression(node)
                
                // Check if there's a catch(CancellationException) - if present, the code is safe
                val hasCancellationExceptionCatch = catchClauses.any { clause ->
                    val parameters = clause.parameters
                    if (parameters.isEmpty()) return@any false
                    val parameter = parameters.first()
                    val typeName = parameter.type.canonicalText
                    CANCELLATION_EXCEPTION_TYPES.any { typeName.contains(it) }
                }
                
                if (hasCancellationExceptionCatch) return super.visitTryExpression(node)
                
                // Look for catch(Exception) or catch(Throwable)
                for (catchClause in catchClauses) {
                    val parameters = catchClause.parameters
                    if (parameters.isEmpty()) continue
                    val parameter = parameters.first()
                    val typeName = parameter.type.canonicalText
                    
                    if (BROAD_EXCEPTION_TYPES.any { typeName.contains(it) }) {
                        // Check if the catch block properly handles cancellation
                        val catchBody = catchClause.body
                        if (!handlesCancellationProperly(catchBody)) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node as UElement),
                                "catch(Exception) may swallow CancellationException. Add catch (e: CancellationException) { throw e } or call ensureActive() in the catch block"
                            )
                            return super.visitTryExpression(node)
                        }
                    }
                }
                
                return super.visitTryExpression(node)
            }
        })
    }
    
    /**
     * Checks if a catch block properly handles cancellation by:
     * - Re-throwing the exception
     * - Calling ensureActive()
     */
    private fun handlesCancellationProperly(body: UExpression): Boolean {
        var hasThrow = false
        var hasEnsureActive = false
        
        body.accept(object : AbstractUastVisitor() {
            override fun visitThrowExpression(node: UThrowExpression): Boolean {
                hasThrow = true
                return super.visitThrowExpression(node)
            }
            
            override fun visitCallExpression(node: UCallExpression): Boolean {
                val methodName = node.methodName
                if (methodName == "ensureActive" || methodName == "currentCoroutineContext") {
                    hasEnsureActive = true
                }
                return super.visitCallExpression(node)
            }
        })
        
        return hasThrow || hasEnsureActive
    }
}
