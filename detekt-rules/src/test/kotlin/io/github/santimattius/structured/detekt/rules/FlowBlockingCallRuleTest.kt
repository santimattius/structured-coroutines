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

class FlowBlockingCallRuleTest {

    private val rule = FlowBlockingCallRule(Config.empty)

    @Test
    fun `reports Thread sleep inside flow builder`() {
        val code = """
            import kotlinx.coroutines.flow.flow
            
            fun test() {
                flow {
                    Thread.sleep(1000)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("Thread.sleep")
        assertThat(findings[0].message).contains("[FLOW_001]")
        assertThat(findings[0].message).contains("flow")
    }

    @Test
    fun `does not report Thread sleep outside flow builder`() {
        val code = """
            fun test() {
                Thread.sleep(1000)
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report delay inside flow builder`() {
        val code = """
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.delay
            
            fun test() {
                flow {
                    delay(100)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports blocking call inside flow with emit`() {
        val code = """
            import kotlinx.coroutines.flow.flow
            import kotlinx.coroutines.flow.emit
            
            fun test() {
                flow {
                    emit(1)
                    Thread.sleep(10)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[FLOW_001]")
    }
}
