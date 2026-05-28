package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class SynchronizedInCoroutineDetectorTest {

    @Test
    fun `reports synchronized inside suspend function`() {
        val code = """
            package test

            import kotlinx.coroutines.*

            suspend fun work(lock: Any) {
                synchronized(lock) { }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(SynchronizedInCoroutineDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectContains("[CONCUR_001]")
    }

    @Test
    fun `does not report mutex withLock`() {
        val mutexStub = """
            package kotlinx.coroutines.sync
            class Mutex
            suspend fun Mutex.withLock(block: () -> Unit) {}
        """.trimIndent()

        val code = """
            package test

            import kotlinx.coroutines.*
            import kotlinx.coroutines.sync.Mutex
            import kotlinx.coroutines.sync.withLock

            suspend fun work(mutex: Mutex) {
                mutex.withLock { }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(mutexStub).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(SynchronizedInCoroutineDetector.ISSUE)
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

            @SuppressLint("SynchronizedInCoroutine")
            suspend fun work(lock: Any) {
                synchronized(lock) { }
            }
        """.trimIndent()

        val androidAnnotation = """
            package android.annotation
            annotation class SuppressLint(vararg val value: String)
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(androidAnnotation).indented(),
                TestFiles.kotlin(code).indented(),
            )
            .issues(SynchronizedInCoroutineDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
