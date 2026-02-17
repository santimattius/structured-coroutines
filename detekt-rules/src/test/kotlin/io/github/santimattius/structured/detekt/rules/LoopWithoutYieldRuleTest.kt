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

class LoopWithoutYieldRuleTest {

    private val rule = LoopWithoutYieldRule(Config.empty)

    @Test
    fun `reports for loop without cooperation point in suspend function`() {
        val code = """
            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    println(item * 2)  // No yield, ensureActive, or delay
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("for")
        assertThat(findings[0].message).contains("cooperation point")
        assertThat(findings[0].message).contains("[CANCEL_001]")
    }

    @Test
    fun `reports while loop without cooperation point in suspend function`() {
        val code = """
            suspend fun processItems() {
                var i = 0
                while (i < 100) {
                    println(i)
                    i++
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("while")
    }

    @Test
    fun `does not report loop with yield`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    yield()
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report loop with ensureActive`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlin.coroutines.coroutineContext
            
            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    ensureActive()
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report loop with delay`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    delay(10)
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report loop in non-suspend function`() {
        val code = """
            fun processItems(items: List<Int>) {
                for (item in items) {
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
