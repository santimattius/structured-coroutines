/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RunBlockingInSuspendRuleTest {

    private val rule = RunBlockingInSuspendRule(Config.empty)

    @Test
    fun `reports runBlocking in suspend function`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun fetchData() {
                runBlocking {
                    delay(1000)
                    loadFromNetwork()
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("runBlocking")
        assertThat(findings[0].message).contains("[RUNBLOCK_002]")
    }

    @Test
    fun `does not report runBlocking in regular function`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun main() = runBlocking {
                delay(1000)
                println("test")
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report runBlocking in test function`() {
        val code = """
            import kotlinx.coroutines.*
            
            @Test
            fun testSomething() = runBlocking {
                val result = fetchData()
                assertEquals(expected, result)
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports nested runBlocking in suspend`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun process() {
                try {
                    runBlocking {
                        doWork()
                    }
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }
}
