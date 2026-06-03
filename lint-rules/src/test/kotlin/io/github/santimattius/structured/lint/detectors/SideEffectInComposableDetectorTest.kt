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

class SideEffectInComposableDetectorTest {

    @Test
    fun warnsOnAnalyticsCallInComposableBody() {
        val code =
            """
            package test

            import androidx.compose.runtime.Composable

            object Analytics {
                fun logScreen(name: String) {}
            }

            @Composable
            fun HomeScreen() {
                Analytics.logScreen("home")
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeScopeAndEffects().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(SideEffectInComposableDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectWarningCount(1)
            .expectContains("[COMPOSE_003]")
    }

    @Test
    fun cleanWhenCallInsideLaunchedEffect() {
        val code =
            """
            package test

            import androidx.compose.runtime.Composable
            import androidx.compose.runtime.LaunchedEffect

            object Analytics {
                fun logScreen(name: String) {}
            }

            @Composable
            fun HomeScreen() {
                LaunchedEffect(Unit) {
                    Analytics.logScreen("home")
                }
            }
            """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.composeScopeAndEffects().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(SideEffectInComposableDetector.ISSUE)
            .allowMissingSdk()
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

            object Analytics {
                fun logScreen(name: String) {}
            }

            @SuppressLint("SideEffectInComposable")
            @Composable
            fun HomeScreen() {
                Analytics.logScreen("home")
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
            .issues(SideEffectInComposableDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.PARENTHESIZED)
            .run()
            .expectClean()
    }
}
