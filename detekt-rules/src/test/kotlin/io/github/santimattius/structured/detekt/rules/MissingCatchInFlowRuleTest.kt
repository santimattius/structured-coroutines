/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MissingCatchInFlowRuleTest {

    private val rule = MissingCatchInFlowRule(Config.empty)

    @Test
    fun `reports map then collect without catch`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            suspend fun demo() {
                flow { emit(1) }
                    .map { it }
                    .collect { }
            }
            """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[FLOW_005]")
    }

    @Test
    fun `clean when catch present upstream`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            suspend fun demo() {
                flow { emit(1) }
                    .map { it }
                    .catch { _: Throwable ->
                    }
                    .collect { }
            }
            """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `clean when enclosing try catches Exception`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            suspend fun demo() {
                try {
                    flow { emit(1) }
                        .map { it }
                        .collect { }
                } catch (e: Exception) {
                    println(e)
                }
            }
            """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `no report for terminal without tracked intermediate operators`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            suspend fun demo() {
                flow { emit(1) }.collect { }
            }
            """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).isEmpty()
    }
}
