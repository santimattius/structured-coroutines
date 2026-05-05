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
import org.junit.Test

class MissingCatchInFlowDetectorTest {

    @Test
    fun warnsOnMapCollectWithoutCatch() {
        val code = """
            package test
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.runBlocking

            fun demo() {
                runBlocking {
                    flow { emit(1) }
                        .map { it }
                        .collect { }
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesAndFlow().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(MissingCatchInFlowDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(1)
            .expectContains("[FLOW_005]")
    }

    @Test
    fun cleanWhenCatchPresent() {
        val code = """
            package test
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.runBlocking

            fun demo() {
                runBlocking {
                    flow { emit(1) }
                        .map { it }
                        .catch { _: Throwable ->
                        }
                        .collect { }
                }
            }
        """.trimIndent()

        val issues = TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesAndFlow().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(MissingCatchInFlowDetector.ISSUE)
            .allowMissingSdk()
            .run()

        issues.expectClean()
    }
}
