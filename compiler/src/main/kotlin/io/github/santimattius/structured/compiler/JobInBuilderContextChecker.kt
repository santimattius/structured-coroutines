/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects `Job()` or `SupervisorJob()` passed directly
 * to coroutine builders.
 *
 * ## Problem (Best Practice 3.3 & 5.1)
 *
 * Passing a new Job directly to coroutine builders breaks the parent-child relationship
 * that is fundamental to structured concurrency:
 *
 * ```kotlin
 * // ❌ BAD: Job() breaks structured concurrency
 * launch(Job()) {
 *     doWork()  // This coroutine is now orphaned!
 * }
 *
 * // ❌ BAD: SupervisorJob() doesn't work as expected
 * withContext(SupervisorJob()) {
 *     // The SupervisorJob becomes an independent parent,
 *     // not integrated into the existing hierarchy
 *     launch { task1() }
 *     launch { task2() }
 * }
 *
 * // ❌ BAD: Trying to "protect" a single launch
 * launch(SupervisorJob()) {
 *     riskyOperation()  // Still not properly supervised!
 * }
 * ```
 *
 * **Problems:**
 * - The new `Job` becomes an independent parent, breaking the hierarchy
 * - The original parent loses control over cancellation
 * - Exceptions don't propagate as expected
 * - Resources may leak if the parent is cancelled
 *
 * ## Why This Doesn't Work
 *
 * When you do `launch(Job())`:
 * 1. A new independent Job is created
 * 2. The launched coroutine becomes a child of this new Job
 * 3. The new Job is NOT a child of the scope's Job
 * 4. Cancelling the scope won't cancel this coroutine
 *
 * ## Recommended Alternatives
 *
 * ### For SupervisorJob Semantics
 *
 * ```kotlin
 * // ✅ GOOD: Use supervisorScope for supervisor behavior
 * suspend fun processAll() = supervisorScope {
 *     launch { task1() }  // Won't cancel siblings on failure
 *     launch { task2() }
 * }
 *
 * // ✅ GOOD: Define a proper scope at the appropriate level
 * class MyService(
 *     private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
 * ) {
 *     fun startTasks() {
 *         scope.launch { task1() }  // Properly supervised
 *         scope.launch { task2() }
 *     }
 * }
 * ```
 *
 * ### For Regular Job Semantics
 *
 * ```kotlin
 * // ✅ GOOD: Just use the scope's Job (default behavior)
 * scope.launch {
 *     doWork()  // Uses parent's Job automatically
 * }
 *
 * // ✅ GOOD: Use coroutineScope for controlled child coroutines
 * suspend fun processSequentially() = coroutineScope {
 *     launch { step1() }
 *     launch { step2() }
 * }
 * ```
 *
 * ## Detection Logic
 *
 * 1. Identifies calls to `launch`, `async`, or `withContext`
 * 2. Checks if any argument is a direct call to `Job()` or `SupervisorJob()`
 * 3. Reports [StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT] if found
 *
 * @see StructuredCoroutinesErrors.JOB_IN_BUILDER_CONTEXT
 * @see <a href="https://kotlinlang.org/docs/exception-handling.html#supervision">Supervision in Coroutines</a>
 */
class JobInBuilderContextChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * Coroutine builders that accept CoroutineContext as a parameter.
         */
        private val COROUTINE_BUILDERS = setOf(
            Name.identifier("launch"),
            Name.identifier("async"),
            Name.identifier("withContext")
        )

        /**
         * Job constructor functions to detect.
         * These break structured concurrency when passed directly to builders.
         */
        private val JOB_CONSTRUCTORS = setOf(
            Name.identifier("Job"),
            Name.identifier("SupervisorJob")
        )
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is a coroutine builder call
        val calleeName = expression.calleeReference.name
        if (calleeName !in COROUTINE_BUILDERS) return

        // Check arguments for Job() or SupervisorJob() calls
        for (argument in expression.arguments) {
            if (argument is FirFunctionCall) {
                val argCalleeName = argument.calleeReference.name
                if (argCalleeName in JOB_CONSTRUCTORS) {
                    reporter.reportJobInBuilderContext(expression, context)
                    return
                }
            }
        }
    }
}
