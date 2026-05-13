/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test as JUnitTest

class RunBlockingInsteadOfRunTestDetectorTest {

    @JUnitTest
    fun warnsOnTestAnnotationWithRunBlockingBody() {
        val code =
            """
            package test

            import kotlinx.coroutines.*

            annotation class Test

            @Test
            fun smoke() = runBlocking {
                println(1)
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RunBlockingInsteadOfRunTestDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(1)
            .expectContains("[TEST_004]")
    }

    @JUnitTest
    fun skipsWhenLambdaUsesDelayLeavingTest001Rule() {
        val code =
            """
            package test

            import kotlinx.coroutines.*

            annotation class Test

            @Test
            fun slow() = runBlocking {
                delay(1)
                println(2)
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RunBlockingInsteadOfRunTestDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @JUnitTest
    fun noWarningWithoutTestAnnotationOnFunction() {
        val code =
            """
            package test

            import kotlinx.coroutines.*

            fun helper() = runBlocking {
                println(1)
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RunBlockingInsteadOfRunTestDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
