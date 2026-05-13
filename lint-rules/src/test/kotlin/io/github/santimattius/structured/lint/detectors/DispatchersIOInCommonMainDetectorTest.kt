/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test as JUnitTest

/**
 * Regression: Lint paths default to synthetic test trees without `commonMain/`; KMP-positive cases
 * are covered by JVM unit tests over [KotlinCommonSourceLintUtils] and heuristic helpers.
 */
class DispatchersIOInCommonMainDetectorTest {

    @JUnitTest
    fun noReportOutsideCommonSourceLayout() {
        val code =
            """
            package test

            import kotlinx.coroutines.*

            val io = Dispatchers.IO
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(DispatchersIOInCommonMainDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @JUnitTest
    fun noReportInAndroidMainSourceLayout() {
        // FP guard: Dispatchers.IO in androidMain is legitimate
        // Lint's synthetic test tree does not embed /androidMain/ in the path,
        // so this verifies the rule does not fire in a standard Android source tree.
        val code =
            """
            package test

            import kotlinx.coroutines.*

            suspend fun readDb() = withContext(Dispatchers.IO) { 42 }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(DispatchersIOInCommonMainDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @JUnitTest
    fun noReportWhenSuppressLintPresent() {
        // Suppression guard: @SuppressLint("DispatchersIOInCommonMain") silences the issue
        val code =
            """
            package test

            import android.annotation.SuppressLint
            import kotlinx.coroutines.*

            @SuppressLint("DispatchersIOInCommonMain")
            suspend fun readDb() = withContext(Dispatchers.IO) { 42 }
            """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(androidAnnotation).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(DispatchersIOInCommonMainDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
