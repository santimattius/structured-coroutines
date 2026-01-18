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
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.expressions.FirQualifiedAccessExpression
import org.jetbrains.kotlin.fir.expressions.arguments
import org.jetbrains.kotlin.name.Name

/**
 * FIR Call Checker that detects usage of `Dispatchers.Unconfined`.
 *
 * ## Problem (Best Practice 3.2)
 *
 * Using `Dispatchers.Unconfined` in production code is problematic because it provides
 * no guarantees about which thread will execute the coroutine:
 *
 * ```kotlin
 * // ⚠️ WARNING: Unpredictable execution thread
 * launch(Dispatchers.Unconfined) {
 *     // May run on ANY thread that resumes this coroutine!
 *     updateUI()  // Dangerous if not on main thread
 * }
 *
 * // ⚠️ WARNING: Thread changes unpredictably
 * withContext(Dispatchers.Unconfined) {
 *     println("Running on: ${Thread.currentThread().name}")
 *     delay(100)  // After this, we might be on a different thread!
 *     println("Now on: ${Thread.currentThread().name}")
 * }
 * ```
 *
 * **Problems:**
 * - The coroutine starts immediately in the current thread (no dispatch)
 * - After suspension, it resumes in whatever thread called `resume()`
 * - This makes it very hard to reason about thread safety
 * - Can accidentally run blocking code on UI thread
 * - Can accidentally access thread-confined resources from wrong thread
 *
 * ## When Unconfined IS Appropriate
 *
 * `Dispatchers.Unconfined` has legitimate uses:
 *
 * ```kotlin
 * // ✅ OK: Testing without dispatcher overhead
 * @Test
 * fun testCoroutine() = runTest(Dispatchers.Unconfined) {
 *     // Fast test execution without real dispatching
 * }
 *
 * // ✅ OK: Performance-critical code that manages its own threading
 * // (rare, requires careful consideration)
 * ```
 *
 * ## Recommended Alternatives
 *
 * ```kotlin
 * // ✅ GOOD: Use appropriate dispatchers
 * launch(Dispatchers.Default) { cpuIntensiveWork() }
 * launch(Dispatchers.IO) { fileOperations() }
 * launch(Dispatchers.Main) { updateUI() }
 *
 * // ✅ GOOD: Use Main.immediate for UI without extra dispatch
 * launch(Dispatchers.Main.immediate) {
 *     // If already on main thread, runs immediately
 *     // Otherwise, dispatches to main thread
 * }
 *
 * // ✅ GOOD: Use withContext for context switches
 * withContext(Dispatchers.IO) {
 *     readFile()
 * }
 * ```
 *
 * ## Note on Severity
 *
 * This checker emits a **WARNING** (not ERROR) because:
 * - `Dispatchers.Unconfined` has valid use cases in testing
 * - Some advanced scenarios may require it
 * - It's a code smell, not always a bug
 *
 * ## Detection Logic
 *
 * 1. Identifies calls to `launch`, `async`, or `withContext`
 * 2. Checks if any argument is `Dispatchers.Unconfined`
 * 3. Reports [StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE] as a warning
 *
 * @see StructuredCoroutinesErrors.DISPATCHERS_UNCONFINED_USAGE
 * @see <a href="https://kotlinlang.org/docs/coroutine-context-and-dispatchers.html#unconfined-vs-confined-dispatcher">Unconfined vs confined dispatcher</a>
 */
class DispatchersUnconfinedChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    companion object {
        /**
         * Coroutine builders and context switchers that accept dispatchers.
         */
        private val CONTEXT_USING_FUNCTIONS = setOf(
            Name.identifier("launch"),
            Name.identifier("async"),
            Name.identifier("withContext")
        )

        /**
         * Name of the Dispatchers object.
         */
        private val DISPATCHERS_NAME = Name.identifier("Dispatchers")

        /**
         * Name of the Unconfined property to detect.
         */
        private val UNCONFINED_NAME = Name.identifier("Unconfined")
    }

    context(context: CheckerContext, reporter: DiagnosticReporter)
    override fun check(expression: FirFunctionCall) {
        // Check if this is a coroutine builder or withContext call
        val calleeName = expression.calleeReference.name
        if (calleeName !in CONTEXT_USING_FUNCTIONS) return

        // Check arguments for Dispatchers.Unconfined
        for (argument in expression.arguments) {
            if (isDispatchersUnconfined(argument)) {
                reporter.reportDispatchersUnconfinedUsage(expression, context)
                return
            }
        }
    }

    /**
     * Checks if an expression is `Dispatchers.Unconfined`.
     *
     * This handles the property access pattern `Dispatchers.Unconfined`
     * where `Unconfined` is a property of the `Dispatchers` object.
     *
     * @param expression The expression to check
     * @return true if this is Dispatchers.Unconfined
     */
    private fun isDispatchersUnconfined(expression: FirExpression): Boolean {
        // Check for property access like Dispatchers.Unconfined
        if (expression is FirPropertyAccessExpression) {
            val propertyName = expression.calleeReference.name
            if (propertyName == UNCONFINED_NAME) {
                // Check if the receiver is Dispatchers
                val receiver = (expression as? FirQualifiedAccessExpression)?.explicitReceiver
                if (receiver is FirPropertyAccessExpression) {
                    return receiver.calleeReference.name == DISPATCHERS_NAME
                }
            }
        }
        return false
    }
}
