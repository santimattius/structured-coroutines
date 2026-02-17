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

class CancellationExceptionSwallowedRuleTest {

    private val rule = CancellationExceptionSwallowedRule(Config.empty)

    @Test
    fun `reports catch Exception inside launch block`() {
        val code = """
            import kotlinx.coroutines.CoroutineScope
            import kotlinx.coroutines.launch

            fun update(scope: CoroutineScope) {
                scope.launch {
                    try {
                        val x = 1
                    } catch (ex: Exception) {
                        val y = 2
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CANCEL_003")
        assertThat(findings[0].message).contains("CancellationException")
    }

    @Test
    fun `reports catch Exception in suspend function`() {
        val code = """
            suspend fun fetch() {
                try {
                    val x = 1
                } catch (e: Exception) {
                    val y = 2
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report when CancellationException is caught first`() {
        val code = """
            import kotlinx.coroutines.CancellationException
            import kotlinx.coroutines.launch
            import kotlinx.coroutines.CoroutineScope

            fun update(scope: CoroutineScope) {
                scope.launch {
                    try {
                        val x = 1
                    } catch (e: CancellationException) {
                        throw e
                    } catch (ex: Exception) {
                        val y = 2
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report catch Exception outside coroutine context`() {
        val code = """
            fun main() {
                try {
                    val x = 1
                } catch (e: Exception) {
                    println(e)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report catch specific exception`() {
        val code = """
            import kotlinx.coroutines.launch
            import kotlinx.coroutines.CoroutineScope

            fun update(scope: CoroutineScope) {
                scope.launch {
                    try {
                        val x = 1
                    } catch (e: IllegalArgumentException) {
                        val y = 2
                    }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
