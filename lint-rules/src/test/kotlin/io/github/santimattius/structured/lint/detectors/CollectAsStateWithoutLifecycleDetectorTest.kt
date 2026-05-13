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

class CollectAsStateWithoutLifecycleDetectorTest {

    @Test
    fun warnsOnComposableFlowCollectAsState() {
        val code =
            """
            package test

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.collectAsState
            import kotlinx.coroutines.flow.StateFlow

            class Vm(val ui: StateFlow<Int>)

            @Composable
            fun Screen(vm: Vm) {
                val s by vm.ui.collectAsState()
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeRuntimeCollectAndFlow().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(CollectAsStateWithoutLifecycleDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(1)
            .expectContains("[COMPOSE_001]")
    }

    @Test
    fun cleanWhenSameComposableIsPreview() {
        val code =
            """
            package test

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.collectAsState
            import androidx.compose.ui.tooling.preview.Preview
            import kotlinx.coroutines.flow.StateFlow

            class Vm(val ui: StateFlow<Int>)

            @Preview
            @Composable
            fun PreviewPane(vm: Vm) {
                val s by vm.ui.collectAsState()
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeRuntimeCollectAndFlow().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(CollectAsStateWithoutLifecycleDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun cleanWhenSuppressLintAnnotationPresent() {
        val code =
            """
            package test

            import android.annotation.SuppressLint
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.collectAsState
            import kotlinx.coroutines.flow.StateFlow

            class Vm(val ui: StateFlow<Int>)

            @SuppressLint("CollectAsStateWithoutLifecycle")
            @Composable
            fun Screen(vm: Vm) {
                val s by vm.ui.collectAsState()
            }
            """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeRuntimeCollectAndFlow().toTypedArray(),
                TestFiles.kotlin(androidAnnotation).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(CollectAsStateWithoutLifecycleDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectClean()
    }
}
