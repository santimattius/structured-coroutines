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
}
