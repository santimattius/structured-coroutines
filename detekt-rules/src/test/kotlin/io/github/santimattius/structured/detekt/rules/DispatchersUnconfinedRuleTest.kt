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

class DispatchersUnconfinedRuleTest {

    private val rule = DispatchersUnconfinedRule(Config.empty)

    @Test
    fun `reports Dispatchers Unconfined in launch`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                scope.launch(Dispatchers.Unconfined) {
                    println("test")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("Dispatchers.Unconfined")
    }

    @Test
    fun `reports Dispatchers Unconfined in async`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                scope.async(Dispatchers.Unconfined) {
                    computeValue()
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `reports Dispatchers Unconfined in withContext`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun test() {
                withContext(Dispatchers.Unconfined) {
                    doWork()
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report Dispatchers Default`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                scope.launch(Dispatchers.Default) {
                    println("test")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report Dispatchers IO`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                scope.launch(Dispatchers.IO) {
                    println("test")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
