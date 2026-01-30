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

class MainDispatcherMisuseDetectorTest {

    @Test
    fun `detects Thread sleep on Main dispatcher`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel : ViewModel() {
                fun test() {
                    viewModelScope.launch(Dispatchers.Main) {
                        Thread.sleep(1000)
                    }
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(MainDispatcherMisuseDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expect("""
src/test/MyViewModel.kt:10: Error: Blocking call on Main dispatcher. Move this to Dispatchers.IO using withContext(Dispatchers.IO) { } [MainDispatcherMisuse]
            Thread.sleep(1000)
            ~~~~~~~~~~~~~~~~~~
1 errors, 0 warnings
            """.trimIndent())
    }

    @Test
    fun `does not detect blocking call on IO dispatcher`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel : ViewModel() {
                fun test() {
                    viewModelScope.launch(Dispatchers.IO) {
                        Thread.sleep(1000)
                    }
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(MainDispatcherMisuseDetector.ISSUE)
            .allowMissingSdk()
            .skipTestModes(TestMode.REORDER_ARGUMENTS)
            .run()
            .expectClean()
    }
}
