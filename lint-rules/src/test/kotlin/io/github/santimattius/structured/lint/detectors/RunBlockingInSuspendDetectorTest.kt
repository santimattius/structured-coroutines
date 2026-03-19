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
import io.github.santimattius.structured.lint.LintTestStubs
import org.junit.Test

class RunBlockingInSuspendDetectorTest {

    @Test
    fun `detects runBlocking in suspend function`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            
            suspend fun fetchData() {
                runBlocking {
                    delay(1000)
                }
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(RunBlockingInSuspendDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expect("""
                src/test/test.kt:6: Error: [RUNBLOCK_002] Remove runBlocking. Use suspend calls directly or withContext(Dispatchers.IO) for blocking work. See: https://github.com/santimattius/structured-coroutines/blob/main/docs/BEST_PRACTICES_COROUTINES.md#22-runblock_002--using-runblocking-inside-suspend-functions [RunBlockingInSuspend]
                    runBlocking {
                    ^
                1 errors, 0 warnings
            """.trimIndent())
    }
    
    @Test
    fun `does not detect runBlocking in non-suspend function`() {
        val code = """
            package test
            
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                delay(1000)
            }
        """.trimIndent()
        
        TestLintTask.lint()
            .files(
                *LintTestStubs.coroutinesOnly().toTypedArray(),
                TestFiles.kotlin(code).indented()
            )
            .issues(RunBlockingInSuspendDetector.ISSUE)
            .allowMissingSdk()
            .run()
            .expectClean()
    }
}
