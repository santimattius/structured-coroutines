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

class InlineCoroutineScopeRuleTest {

    private val rule = InlineCoroutineScopeRule(Config.empty)

    @Test
    fun `reports inline CoroutineScope launch`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test() {
                CoroutineScope(Dispatchers.IO).launch {
                    println("test")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CoroutineScope")
        assertThat(findings[0].message).contains("[SCOPE_003]")
    }

    @Test
    fun `reports inline CoroutineScope async`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test() {
                CoroutineScope(Job()).async {
                    computeValue()
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report regular scope launch`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                scope.launch {
                    println("test")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report coroutineScope launch`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun test() = coroutineScope {
                launch {
                    println("test")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
