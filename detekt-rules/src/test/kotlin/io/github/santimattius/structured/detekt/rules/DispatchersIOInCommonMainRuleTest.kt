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

class DispatchersIOInCommonMainRuleTest {

    private val rule = DispatchersIOInCommonMainRule(Config.empty)

    @Test
    fun `does not report when path is not commonMain`() {
        val code =
            """
            import kotlinx.coroutines.*

            suspend fun read() = withContext(Dispatchers.IO) { 1 }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
