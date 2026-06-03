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

class HardcodedDispatcherInClassRuleTest {

    private val rule = HardcodedDispatcherInClassRule(Config.empty)

    @Test
    fun `reports withContext Dispatchers IO in suspend function`() {
        val code =
            """
            import kotlinx.coroutines.*

            class UserRepository {
                suspend fun fetch() = withContext(Dispatchers.IO) {
                    Unit
                }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[TEST_005]")
    }

    @Test
    fun `does not report when dispatcher is injected`() {
        val code =
            """
            import kotlinx.coroutines.*

            class UserRepository(private val dispatcher: CoroutineDispatcher) {
                suspend fun fetch() = withContext(dispatcher) {
                    Unit
                }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report Dispatchers IO as default parameter value`() {
        val code =
            """
            import kotlinx.coroutines.*

            class Repo(
                private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
            ) {
                suspend fun fetch() = withContext(dispatcher) { Unit }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report when class has IoDispatcher qualifier parameter`() {
        val code =
            """
            import kotlinx.coroutines.*

            class UserRepository(
                @IoDispatcher private val dispatcher: CoroutineDispatcher,
            ) {
                suspend fun fetch() = withContext(Dispatchers.IO) { Unit }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
