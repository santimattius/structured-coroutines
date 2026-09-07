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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.*
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Severity
import io.github.santimattius.structured.lint.utils.CoroutineLintUtils
import io.github.santimattius.structured.lint.utils.LintDocUrl
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
                [CANCEL_003] Catching Exception or Throwable in suspend functions can prevent
                CancellationException from propagating correctly, leaving coroutines
                alive when they should terminate. Handle CancellationException separately:
                add catch (e: CancellationException) { throw e } before the general catch,
                or call ensureActive() in the catch block.

                See: ${LintDocUrl.buildDocLink("43-cancel_003--swallowing-cancellationexception")}
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
    
    // NOTE (bugfix, dead-dispatch registration): this detector previously overrode
    // `SourceCodeScanner.visitClass(context, declaration)` without pairing it with
    // `applicableSuperClasses()` (the only registration mechanism `visitClass` responds to).
    // With neither `applicableSuperClasses()` nor `getApplicableUastTypes()` registered, lint's
    // `UElementVisitor` never added this detector to any dispatch map, so `visitClass` was never
    // invoked and this detector never reported a single diagnostic, in production or in tests.
    // Fixed by switching to the `getApplicableUastTypes()` + `createUastHandler()` pairing used
    // by every other working detector in this module (e.g. LoopWithoutYieldDetector).
    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                node.accept(object : AbstractUastVisitor() {
                    override fun visitTryExpression(tryNode: UTryExpression): Boolean {
                        // Only check inside suspend functions
                        if (!CoroutineLintUtils.isInSuspendFunction(context, tryNode)) {
                            return super.visitTryExpression(tryNode)
                        }

                        val catchClauses = tryNode.catchClauses
                        if (catchClauses.isEmpty()) return super.visitTryExpression(tryNode)

                        // Check if there's a catch(CancellationException) - if present, the code is safe
                        val hasCancellationExceptionCatch = catchClauses.any { clause ->
                            val parameters = clause.parameters
                            if (parameters.isEmpty()) return@any false
                            val parameter = parameters.first()
                            val typeName = parameter.type.canonicalText
                            CANCELLATION_EXCEPTION_TYPES.any { typeName.contains(it) }
                        }

                        if (hasCancellationExceptionCatch) return super.visitTryExpression(tryNode)

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
                                        tryNode,
                                        context.getLocation(tryNode as UElement),
                                        "catch(Exception) may swallow CancellationException. Add catch (e: CancellationException) { throw e } or call ensureActive() in the catch block"
                                    )
                                    return super.visitTryExpression(tryNode)
                                }
                            }
                        }

                        return super.visitTryExpression(tryNode)
                    }
                })
            }
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
