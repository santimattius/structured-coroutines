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
 * [RedundantLaunchInCoroutineScopeDetector] (RUNBLOCK_001) dispatches via
 * `getApplicableMethodNames()` returning `"coroutineScope"` + `visitMethodCall()` — the same
 * working registration pattern used by [GlobalScopeUsageDetector] and the primary check of
 * [LifecycleAwareScopeDetector], not the dead `visitClass()`-without-`applicableSuperClasses()`
 * pattern that affected [LoopWithoutYieldDetector] and three other detectors elsewhere in this
 * chain. No production registration fix is needed here.
 *
 * `LintTestStubs.kotlinxCoroutines` does not declare a `coroutineScope { }` builder function
 * (see [UnstructuredLaunchDetectorTest], which works around the same gap with
 * `allowCompilationErrors()`). This detector's dispatch is keyed on `getApplicableMethodNames()`,
 * which requires Lint to resolve the call to a `PsiMethod` before invoking `visitMethodCall` — an
 * unresolved `coroutineScope` call never fires it. A local stub (mirroring
 * `LifecycleAwareScopeDetectorTest`'s locally-defined `coroutineScopeFactoryStub`) is added here
 * so the call actually resolves and the detector's logic is exercised for real.
 */
class RedundantLaunchInCoroutineScopeDetectorTest {

    private val coroutineScopeBuilderStub = """
        package kotlinx.coroutines
        suspend fun <T> coroutineScope(block: suspend CoroutineScope.() -> T): T = error("stub")
    """.trimIndent()

    private fun stubs() = listOf(
        *LintTestStubs.coroutinesOnly().toTypedArray(),
        TestFiles.kotlin(coroutineScopeBuilderStub).indented(),
    )

    @Test
    fun `reports a single trailing launch in coroutineScope`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun bad() = coroutineScope {
                launch { println("x") }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *stubs().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RedundantLaunchInCoroutineScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report two launches in coroutineScope`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun good() = coroutineScope {
                launch { println("a") }
                launch { println("b") }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *stubs().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RedundantLaunchInCoroutineScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not report a single launch inside a forEach loop in coroutineScope`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun good(xs: List<Int>) = coroutineScope {
                xs.forEach {
                    launch {
                        doSomething(it)
                    }
                }
            }

            suspend fun doSomething(x: Int) {}
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *stubs().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RedundantLaunchInCoroutineScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not report a single launch inside a for loop in coroutineScope`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun good(xs: List<Int>) = coroutineScope {
                for (x in xs) {
                    launch {
                        doSomething(x)
                    }
                }
            }

            suspend fun doSomething(x: Int) {}
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *stubs().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RedundantLaunchInCoroutineScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not report a single launch inside a while loop in coroutineScope`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun good(xs: List<Int>) = coroutineScope {
                var i = 0
                while (i < xs.size) {
                    launch {
                        doSomething(xs[i])
                    }
                    i++
                }
            }

            suspend fun doSomething(x: Int) {}
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *stubs().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RedundantLaunchInCoroutineScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not report direct work in coroutineScope`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun good() = coroutineScope {
                delay(1)
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *stubs().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RedundantLaunchInCoroutineScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
