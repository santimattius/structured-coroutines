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

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.DeclarationCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirClassChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirTryExpressionChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

/**
 * FIR extension that registers additional checkers for structured coroutines validation.
 *
 * This extension provides compile-time enforcement of structured concurrency best practices.
 * All registered checkers analyze FIR (Frontend Intermediate Representation) to detect
 * violations and emit appropriate diagnostics.
 *
 * ## Registered Checkers
 *
 * ### Function Call Checkers ([ExpressionCheckers.functionCallCheckers])
 *
 * | Checker | Rule | Severity |
 * |---------|------|----------|
 * | [UnstructuredLaunchChecker] | GlobalScope, inline scope, unstructured launch | ERROR |
 * | [RunBlockingInSuspendChecker] | runBlocking in suspend functions | ERROR |
 * | [JobInBuilderContextChecker] | Job()/SupervisorJob() in builder context | ERROR |
 * | [DispatchersUnconfinedChecker] | Dispatchers.Unconfined usage | WARNING |
 * | [UnusedDeferredChecker] | async without await | ERROR |
 * | [RedundantLaunchInCoroutineScopeChecker] | redundant launch in coroutineScope | WARNING |
 *
 * ### Try Expression Checkers ([ExpressionCheckers.tryExpressionCheckers])
 *
 * | Checker | Rule | Severity |
 * |---------|------|----------|
 * | [SuspendInFinallyChecker] | Suspend calls in finally without NonCancellable | WARNING |
 * | [CancellationExceptionSwallowedChecker] | catch(Exception) swallowing CancellationException | WARNING |
 *
 * ### Class Checkers ([DeclarationCheckers.classCheckers])
 *
 * | Checker | Rule | Severity |
 * |---------|------|----------|
 * | [CancellationExceptionSubclassChecker] | Classes extending CancellationException | ERROR |
 *
 * ## Best Practices Coverage
 *
 * This extension implements the following best practices from the Coroutines Mastery guide:
 *
 * - **1.1** No GlobalScope usage
 * - **1.3** No inline CoroutineScope creation
 * - **2.2** No runBlocking in suspend functions
 * - **3.2** No Dispatchers.Unconfined in production
 * - **3.3 & 5.1** No Job()/SupervisorJob() in builders
 * - **4.2** Handle CancellationException in catch blocks
 * - **4.3** Use NonCancellable for suspend calls in finally
 * - **5.2** Don't extend CancellationException
 * - **1.2** Use await() with async calls
 * - **2.1** Avoid redundant launch in coroutineScope
 *
 * @param session The FIR session used for symbol resolution
 * @see FirAdditionalCheckersExtension
 */
class ScoroutinesCallCheckerExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {
    
    /**
     * Gets the plugin configuration from the holder.
     */
    private val configuration: PluginConfiguration
        get() = PluginConfigurationHolder.configuration
            ?: PluginConfiguration(org.jetbrains.kotlin.config.CompilerConfiguration())

    /**
     * Expression checkers analyze FIR expressions (function calls, try expressions, etc.)
     */
    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        
        /**
         * Checkers for function call expressions.
         * These detect misuse of coroutine builders and context functions.
         */
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(
            // Rule 1-3: GlobalScope, inline CoroutineScope, unstructured launch (Best Practice 1.1, 1.3)
            UnstructuredLaunchChecker(),
            // Rule 4: runBlocking in suspend functions (Best Practice 2.2)
            RunBlockingInSuspendChecker(),
            // Rule 5: Job()/SupervisorJob() in builder context (Best Practice 3.3 & 5.1)
            JobInBuilderContextChecker(),
            // Rule 6: Dispatchers.Unconfined usage (Best Practice 3.2)
            DispatchersUnconfinedChecker(),
            // Rule 10: async without await (Best Practice 1.2)
            UnusedDeferredChecker(),
            // Rule 11: redundant launch in coroutineScope (Best Practice 2.1)
            RedundantLaunchInCoroutineScopeChecker()
        )

        /**
         * Checkers for try expressions.
         * These detect misuse of exception handling in coroutine context.
         */
        override val tryExpressionCheckers: Set<FirTryExpressionChecker> = setOf(
            // Rule 7: Suspend calls in finally without NonCancellable (Best Practice 4.3)
            SuspendInFinallyChecker(),
            // Rule 8: catch(Exception) swallowing CancellationException (Best Practice 4.2)
            CancellationExceptionSwallowedChecker()
        )
    }

    /**
     * Declaration checkers analyze FIR declarations (classes, functions, properties, etc.)
     */
    override val declarationCheckers: DeclarationCheckers = object : DeclarationCheckers() {
        
        /**
         * Checkers for class declarations.
         * These detect improper class hierarchies related to coroutines.
         */
        override val classCheckers: Set<FirClassChecker> = setOf(
            // Rule 9: Classes extending CancellationException (Best Practice 5.2)
            CancellationExceptionSubclassChecker()
        )
    }
}
