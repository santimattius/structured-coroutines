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

class MissingCoroutineNameRuleTest {

    private val rule = MissingCoroutineNameRule(Config.empty)

    @Test
    fun `reports unnamed launch when rule is active`() {
        val code =
            """
            import kotlinx.coroutines.*

            fun run(scope: CoroutineScope) {
                scope.launch {
                    println("work")
                }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[DEBUG_001]")
    }

    @Test
    fun `does not report when CoroutineName is in context`() {
        val code =
            """
            import kotlinx.coroutines.*

            fun run(scope: CoroutineScope) {
                scope.launch(CoroutineName("load-user")) {
                    println("work")
                }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when file has no coroutines imports`() {
        val code =
            """
            class LocalScope {
                fun launch(block: () -> Unit) {}
                fun run() {
                    launch { println("work") }
                }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
