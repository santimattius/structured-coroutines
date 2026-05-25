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

class RunBlockingInCommonMainDetectorTest {

    @JUnitTest
    fun `reports runBlocking under commonMain path`() {
        val code = """
            package test

            import kotlinx.coroutines.runBlocking

            fun load() = runBlocking { 1 }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin("src/commonMain/kotlin/test/UsesRunBlocking.kt", code).indented(),
            )
            .issues(RunBlockingInCommonMainDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectErrorCount(1)
            .expectContains("[KMP_002]")
            .expectContains("RunBlockingInCommonMain")
    }

    @JUnitTest
    fun `does not report runBlocking outside common source layout`() {
        val code = """
            package test

            import kotlinx.coroutines.runBlocking

            fun load() = runBlocking { 1 }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RunBlockingInCommonMainDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @JUnitTest
    fun `does not report when SuppressLint present`() {
        val code = """
            package test

            import android.annotation.SuppressLint
            import kotlinx.coroutines.runBlocking

            @SuppressLint("RunBlockingInCommonMain")
            fun load() = runBlocking { 1 }
        """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(androidAnnotation).indented(),
                TestFiles.kotlin("src/commonMain/kotlin/test/UsesRunBlocking.kt", code).indented(),
            )
            .issues(RunBlockingInCommonMainDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
