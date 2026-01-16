package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.checkers.expression.ExpressionCheckers
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.analysis.extensions.FirAdditionalCheckersExtension

/**
 * FIR extension that registers additional checkers for structured coroutines validation.
 *
 * This extension adds the [UnstructuredLaunchChecker] to detect improper usage
 * of `launch` and `async` coroutine builders.
 */
class ScoroutinesCallCheckerExtension(session: FirSession) : FirAdditionalCheckersExtension(session) {

    override val expressionCheckers: ExpressionCheckers = object : ExpressionCheckers() {
        override val functionCallCheckers: Set<FirFunctionCallChecker> = setOf(
            UnstructuredLaunchChecker()
        )
    }
}
