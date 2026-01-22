/**
 * Copyright 2024 Santiago Mattiauda
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

/**
 * Detects Job() or SupervisorJob() passed directly to coroutine builders.
 * 
 * Best Practice 3.3 & 5.1: Passing Job()/SupervisorJob() Directly as Context to Builders
 * 
 * Passing a new Job directly to coroutine builders breaks the parent-child relationship
 * that is fundamental to structured concurrency.
 * 
 * Example:
 * ```kotlin
 * // ❌ BAD - Job() breaks structured concurrency
 * scope.launch(Job()) {
 *     doWork()  // This coroutine is now orphaned!
 * }
 * 
 * // ❌ BAD - SupervisorJob() doesn't work as expected
 * withContext(SupervisorJob()) {
 *     launch { task1() }
 *     launch { task2() }
 * }
 * 
 * // ✅ GOOD - Use supervisorScope for supervisor behavior
 * suspend fun processAll() = supervisorScope {
 *     launch { task1() }
 *     launch { task2() }
 * }
 * 
 * // ✅ GOOD - Use the scope's Job (default behavior)
 * scope.launch {
 *     doWork()  // Uses parent's Job automatically
 * }
 * ```
 */
class JobInBuilderContextDetector : Detector(), SourceCodeScanner {
    
    companion object {
        val ISSUE = Issue.create(
            id = "JobInBuilderContext",
            briefDescription = "Job() or SupervisorJob() in coroutine builder",
            explanation = """
                Passing Job() or SupervisorJob() directly to coroutine builders breaks 
                the parent-child relationship that is fundamental to structured concurrency.
                
                The new Job becomes an independent parent, breaking the hierarchy. The original 
                parent loses control over cancellation, exceptions don't propagate as expected, 
                and resources may leak if the parent is cancelled.
                
                Use supervisorScope { } for supervisor semantics, or use the scope's Job 
                (default behavior) for regular coroutines.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                JobInBuilderContextDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        
        private val COROUTINE_BUILDERS = setOf("launch", "async", "withContext")
        private val JOB_CONSTRUCTORS = setOf("Job", "SupervisorJob")
    }
    
    override fun getApplicableMethodNames(): List<String> {
        return COROUTINE_BUILDERS.toList()
    }
    
    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: com.intellij.psi.PsiMethod
    ) {
        // Check arguments for Job() or SupervisorJob() calls
        val arguments = node.valueArguments
        for (arg in arguments) {

            // Check if argument is a call to Job() or SupervisorJob()
            if (arg is UCallExpression) {
                val methodName = arg.methodName
                if (methodName in JOB_CONSTRUCTORS) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Don't pass Job() or SupervisorJob() to coroutine builders. Use supervisorScope { } for supervisor semantics, or use the scope's Job (default behavior)"
                    )
                    return
                }
            }
            
            // Also check if the argument source contains Job() or SupervisorJob()
            val argSource = arg.asSourceString()
            if (argSource.contains("Job()") || argSource.contains("SupervisorJob()")) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Don't pass Job() or SupervisorJob() to coroutine builders. Use supervisorScope { } for supervisor semantics, or use the scope's Job (default behavior)"
                )
                return
            }
        }
    }
}
