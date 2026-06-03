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

class SharedFlowForOneshotEventsRuleTest {

    private val rule = SharedFlowForOneshotEventsRule(Config.empty)

    @Test
    fun `reports default MutableSharedFlow named _events`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            class Vm {
                private val _events = MutableSharedFlow<UiEvent>()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[FLOW_011]")
    }

    @Test
    fun `does not report when replay buffer is configured`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            class Vm {
                private val _events = MutableSharedFlow<UiEvent>(replay = 1)
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when property name is not one-shot pattern`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            class Vm {
                private val _items = MutableSharedFlow<Item>()
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
