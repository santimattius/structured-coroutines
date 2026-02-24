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

class ChannelNotClosedRuleTest {

    private val rule = ChannelNotClosedRule(Config.empty)

    @Test
    fun `reports Channel created without close in same function`() {
        val code = """
            import kotlinx.coroutines.channels.Channel
            
            fun main() {
                val ch = Channel<Int>()
                ch.send(1)
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CHANNEL_001")
        assertThat(findings[0].message).contains("ch")
    }

    @Test
    fun `does not report when close is called in same function`() {
        val code = """
            import kotlinx.coroutines.channels.Channel
            
            fun main() {
                val ch = Channel<Int>()
                try {
                    ch.send(1)
                } finally {
                    ch.close()
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report produce block`() {
        val code = """
            import kotlinx.coroutines.channels.produce
            
            suspend fun flow() = produce {
                send(1)
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports Channel with capacity without close`() {
        val code = """
            import kotlinx.coroutines.channels.Channel
            
            fun process() {
                val channel = Channel<String>(1)
                channel.send("x")
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("channel")
    }

    @Test
    fun `does not report when Channel is not assigned to variable`() {
        val code = """
            import kotlinx.coroutines.channels.Channel
            
            fun process() {
                useChannel(Channel<Int>())
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
