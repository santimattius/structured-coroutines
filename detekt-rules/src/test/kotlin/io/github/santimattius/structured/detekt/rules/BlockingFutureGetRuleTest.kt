/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BlockingFutureGetRuleTest {

    private val rule = BlockingFutureGetRule(Config.empty)

    @Test
    fun `reports Future get in suspend function`() {
        val code =
            """
            import kotlinx.coroutines.*
            import java.util.concurrent.CompletableFuture

            suspend fun load(): String {
                val future = CompletableFuture<String>()
                return future.get()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[INTEROP_004]")
    }

    @Test
    fun `does not report get outside coroutine context`() {
        val code =
            """
            import java.util.concurrent.CompletableFuture

            fun blocking(): String {
                val future = CompletableFuture<String>()
                return future.get()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code =
            """
            import kotlinx.coroutines.*
            import java.util.concurrent.CompletableFuture

            @Suppress("BlockingFutureGet")
            suspend fun load(): String {
                return CompletableFuture<String>().get()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
