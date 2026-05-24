package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class StateInWithEagerlyStrategyDetectorTest {

    @Test
    fun `reports stateIn Eagerly on viewModelScope`() {
        val code = """
            package test

            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.flow.*
            import kotlinx.coroutines.flow.SharingStarted

            class Vm : ViewModel() {
                val ui = flowOf(1).stateIn(viewModelScope, SharingStarted.Eagerly, 0)
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(StateInWithEagerlyStrategyDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectWarningCount(1)
    }

    @Test
    fun `does not report WhileSubscribed`() {
        val code = """
            package test

            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.flow.*
            import kotlinx.coroutines.flow.SharingStarted

            class Vm : ViewModel() {
                val ui = flowOf(1).stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    0,
                )
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(StateInWithEagerlyStrategyDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            package test

            import android.annotation.SuppressLint
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.flow.*
            import kotlinx.coroutines.flow.SharingStarted

            class Vm : ViewModel() {
                @SuppressLint("StateInWithEagerlyStrategy")
                val ui = flowOf(1).stateIn(viewModelScope, SharingStarted.Eagerly, 0)
            }
        """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(androidAnnotation).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(StateInWithEagerlyStrategyDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
