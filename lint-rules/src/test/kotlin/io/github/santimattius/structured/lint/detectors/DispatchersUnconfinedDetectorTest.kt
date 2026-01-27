/**
 * Copyright 2024 Santiago Mattiauda
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
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class DispatchersUnconfinedDetectorTest {

    @Test
    fun `detects Dispatchers Unconfined in launch`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel : ViewModel() {
                fun test() {
                    viewModelScope.launch(Dispatchers.Unconfined) {
                        println("test")
                    }
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(DispatchersUnconfinedDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expect("""
                src/test/MyViewModel.kt:9: Warning: Use an appropriate dispatcher (Dispatchers.Default, Dispatchers.IO, Dispatchers.Main) instead of Dispatchers.Unconfined [DispatchersUnconfined]
                        viewModelScope.launch(Dispatchers.Unconfined) {
                        ^
                0 errors, 1 warnings
            """.trimIndent())
    }
    
    @Test
    fun `does not detect other dispatchers`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel : ViewModel() {
                fun test() {
                    viewModelScope.launch(Dispatchers.IO) {
                        println("test")
                    }
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(DispatchersUnconfinedDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
