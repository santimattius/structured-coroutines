/**
 * Copyright 2026 Santiago Mattiauda
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
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects `runBlocking` calls inside suspend functions.
 *
 * ## Problem (Best Practice 2.2)
 *
 * Calling `runBlocking` from code that's already based on coroutines, especially
 * inside suspend functions, is a serious anti-pattern:
 *
 * ```kotlin
 * // ❌ BAD: runBlocking inside suspend function
 * suspend fun fetchData(): Data {
 *     return runBlocking {  // Blocks the current thread!
 *         api.getData()
 *     }
 * }
 * ```
 *
 * **Problems:**
 * - Blocks the current thread, defeating the purpose of coroutines
 * - Can cause deadlocks if the blocked thread is needed for resumption
 * - Can cause ANRs (Application Not Responding) on Android if on the main thread
 * - Wastes thread resources in a system designed for non-blocking execution
 *
 * ## When runBlocking IS Appropriate
 *
 * `runBlocking` should only be used as a **bridge from blocking code to coroutines**:
 *
 * ```kotlin
 * // ✅ GOOD: Entry point in main function
 * fun main() = runBlocking {
 *     launchApplication()
 * }
 *
 * // ✅ GOOD: Legacy blocking API integration
 * class LegacyService {
 *     fun processSync(): Result = runBlocking {
 *         processAsync()
 *     }
 * }
 *
 * // ✅ GOOD: Test entry point (though runTest is preferred)
 * @Test
 * fun myTest() = runBlocking {
 *     testSuspendFunction()
 * }
 * ```
 *
 * ## Recommended Alternatives
 *
 * Instead of `runBlocking` in suspend functions:
 *
 * ```kotlin
 * // ✅ GOOD: Just call the suspend function directly
 * suspend fun fetchData(): Data {
 *     return api.getData()
 * }
 *
 * // ✅ GOOD: Use withContext for dispatcher changes
 * suspend fun fetchData(): Data {
 *     return withContext(Dispatchers.IO) {
 *         api.getData()
 *     }
 * }
 *
 * // ✅ GOOD: Use coroutineScope for parallel work
 * suspend fun fetchAllData(): List<Data> = coroutineScope {
 *     listOf(
 *         async { api.getData1() },
 *         async { api.getData2() }
 *     ).awaitAll()
 * }
 * ```
 *
 * ## Detection Logic
 *
 * 1. Identifies calls to `runBlocking`
 * 2. Checks if the call is within a `suspend` function
 * 3. Reports [StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND] if both conditions are met
 *
 * @see StructuredCoroutinesErrors.RUN_BLOCKING_IN_SUSPEND
 * @see <a href="https://kotlinlang.org/docs/coroutines-basics.html#bridging-blocking-and-non-blocking-worlds">Bridging blocking and non-blocking worlds</a>
 */
class RunBlockingInSuspendChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * Name of the runBlocking function to detect.
         */
        private val RUN_BLOCKING_NAME = Name.identifier("runBlocking")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is a call to runBlocking
        if (!isRunBlockingCall(expression)) return

        // Check if we're inside a suspend function
        if (isInsideSuspendFunction(context)) {
            reporter.reportRunBlockingInSuspend(expression, context)
        }
    }

    /**
     * Checks if the function call is to `runBlocking`.
     *
     * @param call The function call to check
     * @return true if this is a runBlocking call
     */
    private fun isRunBlockingCall(call: FirFunctionCall): Boolean {
        return call.calleeReference.name == RUN_BLOCKING_NAME
    }

    /**
     * Checks if we're currently inside a suspend function.
     *
     * Walks up the containing declarations hierarchy to find if any
     * enclosing function is marked as `suspend`.
     *
     * @param context The checker context containing declaration hierarchy
     * @return true if we're inside a suspend function
     */
    private fun isInsideSuspendFunction(context: CheckerContext): Boolean {
        // Walk up the containing declarations to find if we're in a suspend function
        for (declaration in context.containingDeclarations) {
            if (declaration is FirSimpleFunction) {
                // Check the status for suspend modifier
                if (declaration.status.isSuspend) {
                    return true
                }
            }
        }
        return false
    }
}
