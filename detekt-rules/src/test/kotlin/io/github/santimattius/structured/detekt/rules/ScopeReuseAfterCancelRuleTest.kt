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

class ScopeReuseAfterCancelRuleTest {

    private val rule = ScopeReuseAfterCancelRule(Config.empty)

    @Test
    fun `reports cancel then launch on same scope`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun process(scope: CoroutineScope) {
                scope.cancel()
                scope.launch { println("x") }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CANCEL_005")
        assertThat(findings[0].message).contains("reused")
    }

    @Test
    fun `reports cancel then async on same scope`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun process(scope: CoroutineScope) {
                scope.cancel()
                scope.async { 1 }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report when scope not reused after cancel`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun cleanup(scope: CoroutineScope) {
                scope.cancel()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when launch before cancel`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun process(scope: CoroutineScope) {
                scope.launch { println("x") }
                scope.cancel()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report different scope after cancel`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun process(scope1: CoroutineScope, scope2: CoroutineScope) {
                scope1.cancel()
                scope2.launch { }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
