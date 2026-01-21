/**
 * Copyright 2024 Santiago Mattiauda
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

class GlobalScopeUsageRuleTest {

    private val rule = GlobalScopeUsageRule(Config.empty)

    @Test
    fun `reports GlobalScope launch`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test() {
                GlobalScope.launch {
                    println("test")
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("GlobalScope")
    }

    @Test
    fun `reports GlobalScope async`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test() {
                GlobalScope.async {
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
