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

class MutableFlowExposedRuleTest {

    private val rule = MutableFlowExposedRule(Config.empty)

    @Test
    fun `reports public MutableStateFlow`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            class Vm {
                val state = MutableStateFlow(0)
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[FLOW_010]")
    }

    @Test
    fun `does not report private backing field`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            class Vm {
                private val _state = MutableStateFlow(0)
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `explicit type MutableSharedFlow exposes API`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            class Bus {
                val events: MutableSharedFlow<String> = MutableSharedFlow()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }
}
