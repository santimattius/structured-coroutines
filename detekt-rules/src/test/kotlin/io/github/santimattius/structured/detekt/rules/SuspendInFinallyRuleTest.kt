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

class SuspendInFinallyRuleTest {

    private val rule = SuspendInFinallyRule(Config.empty)

    @Test
    fun `reports delay in finally without NonCancellable`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun bad() {
                try {
                    delay(1)
                } finally {
                    delay(1)
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CANCEL_004")
    }

    @Test
    fun `does not report when finally uses withContext NonCancellable`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun good() {
                try {
                    delay(1)
                } finally {
                    withContext(NonCancellable) {
                        delay(1)
                    }
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports withContext in finally when not NonCancellable`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun bad() {
                try {
                    delay(1)
                } finally {
                    withContext(Dispatchers.IO) {
                        delay(1)
                    }
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report non-suspend in finally`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun ok() {
                try {
                    delay(1)
                } finally {
                    println("cleanup")
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
