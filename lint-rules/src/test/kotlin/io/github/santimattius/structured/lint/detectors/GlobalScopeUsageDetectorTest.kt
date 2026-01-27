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

class GlobalScopeUsageDetectorTest {

    @Test
    fun `detects GlobalScope launch`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            
            fun test() {
                GlobalScope.launch {
                    println("test")
                }
            }
        """.trimIndent()

        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(GlobalScopeUsageDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expect("""
                src/test/test.kt:6: Error: Use viewModelScope, lifecycleScope, rememberCoroutineScope(), or coroutineScope { } instead of GlobalScope [GlobalScopeUsage]
                    GlobalScope.launch {
                    ^
                1 errors, 0 warnings
            """.trimIndent())
    }
    
    @Test
    fun `detects GlobalScope async`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            
            fun test() {
                GlobalScope.async {
                    "result"
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(GlobalScopeUsageDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expect("""
                src/test/test.kt:6: Error: Use viewModelScope, lifecycleScope, rememberCoroutineScope(), or coroutineScope { } instead of GlobalScope [GlobalScopeUsage]
                    GlobalScope.async {
                    ^
                1 errors, 0 warnings
            """.trimIndent())
    }
    
    @Test
    fun `does not detect viewModelScope`() {
        val code = """
            package test
            
            import androidx.lifecycle.ViewModel
            import androidx.lifecycle.viewModelScope
            import kotlinx.coroutines.launch
            
            class MyViewModel : ViewModel() {
                fun load() {
                    viewModelScope.launch {
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
            .issues(GlobalScopeUsageDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
