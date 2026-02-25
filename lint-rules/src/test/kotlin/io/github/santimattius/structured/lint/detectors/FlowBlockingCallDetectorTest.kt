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
import com.android.tools.lint.checks.infrastructure.TestMode
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class FlowBlockingCallDetectorTest {

    @Test
    fun reportsBlockingCallInsideFlow() {
        val code = """
            package test
            import kotlinx.coroutines.flow.flow
            
            fun main() {
                flow {
                    Thread.sleep(1)
                }
            }
        """.trimIndent()
        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesAndFlow().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(FlowBlockingCallDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(1)
            .expectContains("[FLOW_001]")
    }

    @Test
    fun doesNotReportWhenNoFlow() {
        val code = """
            package test
            
            fun main() {
                Thread.sleep(1)
            }
        """.trimIndent()
        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(FlowBlockingCallDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
