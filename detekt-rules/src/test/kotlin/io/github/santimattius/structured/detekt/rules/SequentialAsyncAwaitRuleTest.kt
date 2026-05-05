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

class SequentialAsyncAwaitRuleTest {

    private val rule = SequentialAsyncAwaitRule(Config.empty)

    @Test
    fun `reports async await on same chain`() {
        val code =
            """
            import kotlinx.coroutines.*

            suspend fun load(): Int = coroutineScope {
                async { 1 }.await()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[CONCUR_003]")
    }

    @Test
    fun `does not report parallel async`() {
        val code =
            """
            import kotlinx.coroutines.*

            suspend fun load() = coroutineScope {
                val a = async { 1 }
                val b = async { 2 }
                a.await() + b.await()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `no coroutines import no report`() {
        val code =
            """
            fun x() = 1
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
