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

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

/**
 * SPIKE (bounded verification, see task description): confirms whether
 * [LoopWithoutYieldDetector] correctly flags a `while` loop whose only apparent cooperation
 * point is `delay()` inside a local `suspend fun` that is declared but never called.
 *
 * The compiler's FIR checker ([io.github.santimattius.structured.compiler.LoopWithoutYieldChecker])
 * was fixed for exactly this case by #66 by pruning nested `FirNamedFunction`/`FirPropertyAccessor`
 * declarations before searching for a cooperation point. This detector's
 * `hasCooperationPoint` walk (an unpruned `AbstractUastVisitor`) has no equivalent pruning, so
 * it is hypothesized to still find `delay(1)` as a descendant of the loop body and incorrectly
 * treat the loop as safe, even though `helper()` is dead code and never executes during
 * iteration.
 */
class LoopWithoutYieldDetectorTest {

    @Test
    fun `reports loop with only an uncalled local suspend fun containing delay`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun processItems() {
                var i = 0
                while (i < 100) {
                    suspend fun helper() {
                        delay(1)
                    }
                    println(i)
                    i++
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LoopWithoutYieldDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report loop when a real cooperation point coexists with a local suspend fun declaration`() {
        // Negative counterpart to the spike above, adapted to what this detector can actually
        // recognize: unlike the compiler (real FIR suspend-call resolution) and Detekt (a naming
        // heuristic for suspend calls), this detector's `hasCooperationPoint` only matches a
        // fixed, literal set of names (COOPERATION_POINTS) — it does not resolve whether an
        // arbitrarily-named call like `helper()` is itself suspend. So a call to the local
        // `helper()` cannot be recognized as a cooperation point here regardless of pruning; the
        // meaningful over-broad-pruning guard for this surface is that pruning the (unrelated,
        // declared-but-uncalled) `helper` declaration must not swallow a genuine, separate
        // cooperation-point call (`delay(20)`) that exists elsewhere in the same loop body.
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun processItems() {
                var i = 0
                while (i < 100) {
                    suspend fun helper() {
                        delay(1)
                    }
                    delay(20)
                    println(i)
                    i++
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LoopWithoutYieldDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not report loop with a real top-level delay call`() {
        // Sanity/regression guard: the most basic passing case (a direct cooperation-point call
        // in the loop body, no nested declaration involved) must keep passing after pruning is
        // introduced.
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun processItems() {
                var i = 0
                while (i < 100) {
                    delay(10)
                    println(i)
                    i++
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LoopWithoutYieldDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
