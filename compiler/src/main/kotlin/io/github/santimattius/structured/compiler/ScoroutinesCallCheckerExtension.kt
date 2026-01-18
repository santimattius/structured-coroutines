package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

/**
 * FIR extension that registers additional checkers for structured coroutines validation.
 *
 * Checkers registered:
 * 1. [UnstructuredLaunchChecker] - Detects improper usage of launch/async
 * 2. [RunBlockingInSuspendChecker] - Detects runBlocking inside suspend functions
 * 3. [JobInBuilderContextChecker] - Detects Job()/SupervisorJob() passed to builders
 * 4. [DispatchersUnconfinedChecker] - Detects Dispatchers.Unconfined usage
 */
class ScoroutinesCallCheckerExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(
            // Rule 1-3: GlobalScope, inline CoroutineScope, unstructured launch
            UnstructuredLaunchChecker(),
            // Rule 4: runBlocking in suspend functions (Best Practice 2.2)
            RunBlockingInSuspendChecker(),
            // Rule 5: Job()/SupervisorJob() in builder context (Best Practice 3.3 & 5.1)
            JobInBuilderContextChecker(),
            // Rule 6: Dispatchers.Unconfined usage (Best Practice 3.2)
            DispatchersUnconfinedChecker()
        )
    }
}
