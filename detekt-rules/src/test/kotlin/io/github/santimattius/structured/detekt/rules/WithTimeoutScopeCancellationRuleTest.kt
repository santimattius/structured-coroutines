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

class WithTimeoutScopeCancellationRuleTest {

    private val rule = WithTimeoutScopeCancellationRule(Config.empty)

    // ── Positive tests (should report) ──────────────────────────────────────

    @Test
    fun `reports withTimeout without try-catch`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetchData(): String {
                return withTimeout(5_000) {
                    "result"
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CANCEL_006")
        assertThat(findings[0].message).contains("withTimeout")
    }

    @Test
    fun `reports withTimeout inside coroutine builder without catch`() {
        val code = """
            import kotlinx.coroutines.*

            fun startWork(scope: CoroutineScope) {
                scope.launch {
                    withTimeout(3_000) {
                        doWork()
                    }
                }
            }

            suspend fun doWork() {}
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CANCEL_006")
    }

    @Test
    fun `reports withTimeout in try block whose catch does not cover TimeoutCancellationException`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetch(): String? {
                return try {
                    withTimeout(5_000) { "ok" }
                } catch (e: IllegalStateException) {
                    null
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    // ── Negative tests (should NOT report) ──────────────────────────────────

    @Test
    fun `does not report withTimeout caught with TimeoutCancellationException`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetch(): String? {
                return try {
                    withTimeout(5_000) { "ok" }
                } catch (e: TimeoutCancellationException) {
                    null
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report withTimeout caught with CancellationException`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetch(): String? {
                return try {
                    withTimeout(5_000) { "ok" }
                } catch (e: CancellationException) {
                    null
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report withTimeout caught with Exception`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetch(): String? {
                return try {
                    withTimeout(5_000) { "ok" }
                } catch (e: Exception) {
                    null
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report withTimeout caught with Throwable`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetch(): String? {
                return try {
                    withTimeout(5_000) { "ok" }
                } catch (e: Throwable) {
                    null
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report withTimeoutOrNull`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetch(): String? {
                return withTimeoutOrNull(5_000) { "ok" }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report withTimeout in multi-catch that covers TimeoutCancellationException`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun fetch(): String? {
                return try {
                    withTimeout(5_000) { "ok" }
                } catch (e: IllegalArgumentException) {
                    null
                } catch (e: TimeoutCancellationException) {
                    null
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
