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

class UnusedDeferredRuleTest {

    private val rule = UnusedDeferredRule(Config.empty)

    @Test
    fun `reports async when result not awaited`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("SCOPE_002")
    }

    @Test
    fun `does not report async when awaited`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                d.await()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async when result used in awaitAll`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun test(scope: CoroutineScope) {
                val a = scope.async { 1 }
                val b = scope.async { 2 }
                awaitAll(a, b)
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report launch`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                scope.launch { }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
