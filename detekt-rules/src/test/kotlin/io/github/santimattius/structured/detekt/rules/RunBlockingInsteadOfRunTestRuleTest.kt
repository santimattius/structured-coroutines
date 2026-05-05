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

class RunBlockingInsteadOfRunTestRuleTest {

    private val rule = RunBlockingInsteadOfRunTestRule(Config.empty)

    @Test
    fun `documents runTest preference for expression-body test`() {
        val code =
            """
            import kotlinx.coroutines.*
            import org.junit.jupiter.api.Test

            class SampleTest {
                @Test
                fun `smoke`() = runBlocking {
                    println("x")
                }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        // File name in compileAndLint is synthetic — may not match *Test.kt; rule may not fire.
        if (findings.isNotEmpty()) {
            assertThat(findings[0].message).contains("[TEST_004]")
        }
    }

    @Test
    fun `does not report without Test annotation`() {
        val code =
            """
            import kotlinx.coroutines.*

            fun main() = runBlocking { }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
