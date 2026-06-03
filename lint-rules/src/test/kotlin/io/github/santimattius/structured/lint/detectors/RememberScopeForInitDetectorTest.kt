/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import com.android.tools.lint.checks.infrastructure.TestMode
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class RememberScopeForInitDetectorTest {

    @Test
    fun warnsOnScopeLaunchInComposableBody() {
        val code =
            """
            package test

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.rememberCoroutineScope
            import kotlinx.coroutines.launch

            class Vm {
                fun load() {}
            }

            @Composable
            fun Screen(vm: Vm) {
                val scope = rememberCoroutineScope()
                scope.launch { vm.load() }
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeScopeAndEffects().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RememberScopeForInitDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(1)
            .expectContains("[COMPOSE_002]")
    }

    @Test
    fun cleanWhenLaunchInsideOnClick() {
        val code =
            """
            package test

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.rememberCoroutineScope
            import kotlinx.coroutines.launch

            @Composable
            fun Screen() {
                val scope = rememberCoroutineScope()
                Button(onClick = { scope.launch { } }) { }
            }

            @Composable
            fun Button(onClick: () -> Unit, content: @Composable () -> Unit) {}
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeScopeAndEffects().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RememberScopeForInitDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectClean()
    }

    @Test
    fun cleanWhenSuppressLintPresent() {
        val code =
            """
            package test

            import android.annotation.SuppressLint
            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.rememberCoroutineScope
            import kotlinx.coroutines.launch

            @SuppressLint("RememberScopeForInit")
            @Composable
            fun Screen() {
                val scope = rememberCoroutineScope()
                scope.launch { }
            }
            """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeScopeAndEffects().toTypedArray(),
                TestFiles.kotlin(androidAnnotation).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(RememberScopeForInitDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectClean()
    }
}
