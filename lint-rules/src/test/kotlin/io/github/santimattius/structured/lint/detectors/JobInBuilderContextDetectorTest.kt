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
import org.junit.Test

class JobInBuilderContextDetectorTest {
    
    @Test
    fun `detects Job in launch`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel {
                fun test() {
                    viewModelScope.launch(Job()) {
                        println("test")
                    }
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(TestFiles.kotlin(code))
            .issues(JobInBuilderContextDetector.ISSUE)
            .run()
            .expect("""
                src/test/test.kt:8: Error: Don't pass Job() or SupervisorJob() to coroutine builders. Use supervisorScope { } for supervisor semantics, or use the scope's Job (default behavior) [JobInBuilderContext]
                viewModelScope.launch(Job()) {
                ^
            """.trimIndent())
    }
    
    @Test
    fun `detects SupervisorJob in async`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel {
                fun test() {
                    viewModelScope.async(SupervisorJob()) {
                        "result"
                    }
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(TestFiles.kotlin(code))
            .issues(JobInBuilderContextDetector.ISSUE)
            .run()
            .expect("""
                src/test/test.kt:8: Error: Don't pass Job() or SupervisorJob() to coroutine builders. Use supervisorScope { } for supervisor semantics, or use the scope's Job (default behavior) [JobInBuilderContext]
                viewModelScope.async(SupervisorJob()) {
                ^
            """.trimIndent())
    }
}
