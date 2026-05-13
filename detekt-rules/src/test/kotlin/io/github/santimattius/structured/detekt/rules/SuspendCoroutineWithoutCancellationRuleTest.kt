/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SuspendCoroutineWithoutCancellationRuleTest {

    private val rule = SuspendCoroutineWithoutCancellationRule(Config.empty)

    @Test
    fun `reports suspendCoroutine in suspend fun`() {
        val code =
            """
            import kotlinx.coroutines.*

            suspend fun load(): Int = suspendCoroutine { c ->
                c.resume(1)
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[INTEROP_001]")
    }

    @Test
    fun `does not fire without coroutines import`() {
        val code =
            """
            suspend fun load(): Unit = Unit
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `suppress message contains doc reference`() {
        val code =
            """
            import kotlinx.coroutines.*

            suspend fun x() = suspendCoroutine<Unit> { }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("suspendCancellableCoroutine")
    }
}
