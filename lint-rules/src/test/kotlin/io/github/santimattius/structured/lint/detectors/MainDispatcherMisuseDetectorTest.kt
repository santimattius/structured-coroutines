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

class MainDispatcherMisuseDetectorTest {
    
    @Test
    fun `detects Thread sleep on Main dispatcher`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel {
                fun test() {
                    viewModelScope.launch(Dispatchers.Main) {
                        Thread.sleep(1000)
                    }
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(TestFiles.kotlin(code))
            .issues(MainDispatcherMisuseDetector.ISSUE)
            .run()
            .expect("""
                src/test/test.kt:9: Error: Blocking call on Main dispatcher. Move this to Dispatchers.IO using withContext(Dispatchers.IO) { } [MainDispatcherMisuse]
                Thread.sleep(1000)
                ^
            """.trimIndent())
    }
    
    @Test
    fun `does not detect blocking call on IO dispatcher`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            import androidx.lifecycle.viewModelScope
            
            class MyViewModel {
                fun test() {
                    viewModelScope.launch(Dispatchers.IO) {
                        Thread.sleep(1000)
                    }
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(TestFiles.kotlin(code))
            .issues(MainDispatcherMisuseDetector.ISSUE)
            .run()
            .expectClean()
    }
}
