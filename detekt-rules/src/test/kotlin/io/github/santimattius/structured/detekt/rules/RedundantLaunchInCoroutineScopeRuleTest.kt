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

class RedundantLaunchInCoroutineScopeRuleTest {

    private val rule = RedundantLaunchInCoroutineScopeRule(Config.empty)

    @Test
    fun `reports single launch in coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun bad() = coroutineScope {
                launch { println("x") }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("RUNBLOCK_001")
        assertThat(findings[0].message).contains("Redundant")
    }

    @Test
    fun `reports single launch in supervisorScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun bad() = supervisorScope {
                launch { println("x") }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report multiple launches in coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun good() = coroutineScope {
                launch { println("a") }
                launch { println("b") }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report launch and async in coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun good() = coroutineScope {
                launch { println("a") }
                async { 1 }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report direct work in coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun good() = coroutineScope {
                delay(1)
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report launch inside forEach in coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun good(xs: List<Int>) = coroutineScope {
                xs.forEach {
                    launch {
                        doSomething(it)
                    }
                }
            }
            
            suspend fun doSomething(x: Int) {}
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report launch inside for loop in coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun good(xs: List<Int>) = coroutineScope {
                for (x in xs) {
                    launch {
                        doSomething(x)
                    }
                }
            }
            
            suspend fun doSomething(x: Int) {}
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
