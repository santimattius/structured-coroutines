package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class LaunchInWithUnstructuredScopeDetectorTest {

    @Test
    fun `reports launchIn on GlobalScope`() {
        val code = """
            package test

            import kotlinx.coroutines.*
            import kotlinx.coroutines.flow.*

            fun start(flow: Flow<Int>) {
                flow.launchIn(GlobalScope)
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesAndFlow().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LaunchInWithUnstructuredScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectContains("[FLOW_007]")
    }

    @Test
    fun `does not report launchIn on viewModelScope`() {
        val code = """
            package test

            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.*
            import kotlinx.coroutines.flow.*

            class Vm : ViewModel() {
                fun start(flow: Flow<Int>) {
                    flow.launchIn(viewModelScope)
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LaunchInWithUnstructuredScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            package test

            import android.annotation.SuppressLint
            import kotlinx.coroutines.*
            import kotlinx.coroutines.flow.*

            @SuppressLint("LaunchInWithUnstructuredScope")
            fun start(flow: Flow<Int>) {
                flow.launchIn(GlobalScope)
            }
        """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesAndFlow().toTypedArray(),
                TestFiles.kotlin(androidAnnotation).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(LaunchInWithUnstructuredScopeDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
