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

class UnstructuredLaunchDetectorTest {

    // ── True positives ──────────────────────────────────────────────────────

    @Test
    fun `detects scope launch without annotation`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            fun process(scope: CoroutineScope) {
                scope.launch { doWork() }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(UnstructuredLaunchDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectContains("UnstructuredLaunch")
    }

    // ── False positive guards ───────────────────────────────────────────────

    @Test
    fun `does not flag viewModelScope launch`() {
        val code = """
            package test

            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.launch

            class MyViewModel : ViewModel() {
                fun load() {
                    viewModelScope.launch { println("ok") }
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(UnstructuredLaunchDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not flag rememberCoroutineScope inline launch`() {
        val code = """
            package test

            import androidx.compose.runtime.rememberCoroutineScope
            import kotlinx.coroutines.launch

            fun MyComposable() {
                rememberCoroutineScope().launch { println("ok") }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(LintTestStubs.composeRuntime).indented(),
                TestFiles.kotlin(code).indented()
            )
            .issues(UnstructuredLaunchDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not flag rememberCoroutineScope stored in local variable`() {
        val code = """
            package test

            import androidx.compose.runtime.rememberCoroutineScope
            import kotlinx.coroutines.launch

            fun MyComposable() {
                val scope = rememberCoroutineScope()
                scope.launch { println("ok") }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(LintTestStubs.composeRuntime).indented(),
                TestFiles.kotlin(code).indented()
            )
            .issues(UnstructuredLaunchDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not flag rememberCoroutineScope captured into lambda`() {
        val code = """
            package test

            import androidx.compose.runtime.rememberCoroutineScope
            import kotlinx.coroutines.launch

            fun MyComposable() {
                val scope = rememberCoroutineScope()
                SomeWidget(
                    onDismiss = {
                        scope.launch { doWork() }
                    }
                )
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(LintTestStubs.composeRuntime).indented(),
                TestFiles.kotlin(code).indented()
            )
            .issues(UnstructuredLaunchDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not flag coroutineScope builder`() {
        // launch without an explicit receiver inside coroutineScope { } cannot be resolved
        // by Lint without the full kotlinx-coroutines classpath; allowCompilationErrors()
        // lets the test verify that no UnstructuredLaunch warning is emitted.
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun process() = coroutineScope {
                launch { doWork() }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(UnstructuredLaunchDetector.ISSUE)
            .allowMissingSdk()
            .allowCompilationErrors()
            .run()
            .expectClean()
    }
}
