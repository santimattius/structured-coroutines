/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package io.github.santimattius.structured.detekt.rules

import dev.detekt.api.Config
import dev.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CallbackFlowWithoutAwaitCloseRuleTest {

    private val rule = CallbackFlowWithoutAwaitCloseRule(Config.empty)

    @Test
    fun `reports callbackFlow without awaitClose`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            fun signals(): Flow<Int> = callbackFlow {
                trySend(1)
            }
            """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[INTEROP_002]")
    }

    @Test
    fun `does not report when awaitClose present`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            fun signals(): Flow<Int> = callbackFlow {
                trySend(1)
                awaitClose { }
            }
            """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `inactive when no flow imports`() {
        val code =
            """
            fun noop() = Unit
            """.trimIndent()

        val findings = rule.lint(code)
        assertThat(findings).isEmpty()
    }
}
