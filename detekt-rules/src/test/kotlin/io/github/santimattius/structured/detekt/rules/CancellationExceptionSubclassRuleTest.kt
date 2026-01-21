/**
 * Copyright 2024 Santiago Mattiauda
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

class CancellationExceptionSubclassRuleTest {

    private val rule = CancellationExceptionSubclassRule(Config.empty)

    @Test
    fun `reports class extending CancellationException`() {
        val code = """
            import kotlinx.coroutines.CancellationException
            
            class UserNotFoundException : CancellationException("User not found")
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("CancellationException")
    }

    @Test
    fun `reports class extending CancellationException with fully qualified name`() {
        val code = """
            import kotlinx.coroutines.CancellationException
            
            class MyError : kotlinx.coroutines.CancellationException("Error")
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report class extending Exception`() {
        val code = """
            class UserNotFoundException : Exception("User not found")
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report class extending RuntimeException`() {
        val code = """
            class MyError : RuntimeException("Error")
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports multiple classes extending CancellationException`() {
        val code = """
            import kotlinx.coroutines.CancellationException
            
            class Error1 : CancellationException("Error 1")
            class Error2 : CancellationException("Error 2")
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(2)
    }
}
