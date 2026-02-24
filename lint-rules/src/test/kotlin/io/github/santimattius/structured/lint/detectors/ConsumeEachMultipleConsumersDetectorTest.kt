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

class ConsumeEachMultipleConsumersDetectorTest {

    @Test
    fun `reports when same channel consumeEach from multiple launch`() {
        val code = """
            package test
            import kotlinx.coroutines.*
            import kotlinx.coroutines.channels.*
            fun main(scope: CoroutineScope) {
                val ch = Channel<Int>()
                scope.launch { ch.consumeEach { } }
                scope.launch { ch.consumeEach { } }
            }
        """.trimIndent()
        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesAndChannels().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(ConsumeEachMultipleConsumersDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(2)
    }

    @Test
    fun `does not report when single consumer uses consumeEach`() {
        val code = """
            package test
            import kotlinx.coroutines.*
            import kotlinx.coroutines.channels.*
            fun main(scope: CoroutineScope) {
                val ch = Channel<Int>()
                scope.launch { ch.consumeEach { } }
            }
        """.trimIndent()
        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesAndChannels().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(ConsumeEachMultipleConsumersDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
