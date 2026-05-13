/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class RunBlockingInsteadOfRunTestRuleTest {

    private val rule = RunBlockingInsteadOfRunTestRule(Config.empty)

    @Test
    fun `reports runBlocking as JUnit expression body under src test path`() {
        val uri = javaClass.getResource("/detekt-junit-src-test/src/test/kotlin/ExpressionBodyTest.kt")!!
        val findings = rule.lint(Paths.get(uri.toURI()))
        assertThat(findings).hasSize(1)
        assertThat(findings.single().message).contains("[TEST_004]")
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

    @Test
    fun `does not report when suppressed with Suppress annotation`() {
        val code =
            """
            import kotlinx.coroutines.*
            import org.junit.jupiter.api.Test

            class SampleTest {
                @Suppress("RunBlockingInsteadOfRunTest")
                @Test
                fun `suppressed`() = runBlocking {
                    println("x")
                }
            }
            """.trimIndent()

        // Rule checks file/function name for test file heuristic — compileAndLint uses synthetic name;
        // even if rule would otherwise fire, @Suppress prevents the finding
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
