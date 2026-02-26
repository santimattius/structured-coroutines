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

class ConsumeEachMultipleConsumersRuleTest {

    private val rule = ConsumeEachMultipleConsumersRule(Config.empty)

    @Test
    fun `reports when same channel consumeEach from multiple launch`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlinx.coroutines.channels.*
            
            fun main(scope: CoroutineScope) {
                val ch = Channel<Int>()
                scope.launch { ch.consumeEach { } }
                scope.launch { ch.consumeEach { } }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(2)
        assertThat(findings[0].message).contains("CHANNEL_002")
        assertThat(findings[0].message).contains("ch")
    }

    @Test
    fun `reports when same channel consumeEach from launch and async`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlinx.coroutines.channels.*
            
            fun main(scope: CoroutineScope) {
                val ch = Channel<Int>()
                scope.launch { ch.consumeEach { } }
                scope.async { ch.consumeEach { } }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(2)
        assertThat(findings.all { it.message.contains("CHANNEL_002") }).isTrue()
    }

    @Test
    fun `does not report when single consumer uses consumeEach`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlinx.coroutines.channels.*
            
            fun main(scope: CoroutineScope) {
                val ch = Channel<Int>()
                scope.launch { ch.consumeEach { } }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when different channels use consumeEach`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlinx.coroutines.channels.*
            
            fun main(scope: CoroutineScope) {
                val ch1 = Channel<Int>()
                val ch2 = Channel<Int>()
                scope.launch { ch1.consumeEach { } }
                scope.launch { ch2.consumeEach { } }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports nested launch with same channel consumeEach`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlinx.coroutines.channels.*
            
            fun main(scope: CoroutineScope) {
                val ch = Channel<Int>()
                scope.launch {
                    launch { ch.consumeEach { } }
                }
                scope.async { ch.consumeEach { } }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(2)
    }
}
