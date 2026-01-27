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

class JobInBuilderContextDetectorTest {

    @Test
    fun `detects Job in launch`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel : ViewModel() {
                fun test() {
                    viewModelScope.launch(Job()) {
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
            .issues(JobInBuilderContextDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expect("""
                src/test/MyViewModel.kt:9: Error: Don't pass Job() or SupervisorJob() to coroutine builders. Use supervisorScope { } for supervisor semantics, or use the scope's Job (default behavior) [JobInBuilderContext]
                        viewModelScope.launch(Job()) {
                        ^
                1 errors, 0 warnings
            """.trimIndent())
    }

    @Test
    fun `detects SupervisorJob in async`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel : ViewModel() {
                fun test() {
                    viewModelScope.async(SupervisorJob()) {
                        "result"
                    }
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(
                *LintTestStubs.all().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(JobInBuilderContextDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expect("""
                src/test/MyViewModel.kt:9: Error: Don't pass Job() or SupervisorJob() to coroutine builders. Use supervisorScope { } for supervisor semantics, or use the scope's Job (default behavior) [JobInBuilderContext]
                        viewModelScope.async(SupervisorJob()) {
                        ^
                1 errors, 0 warnings
            """.trimIndent())
    }
}
