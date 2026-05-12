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

    @Test
    fun `does not report Dispatchers IO in jvmMain path`() {
        // FP guard: jvmMain code may legitimately use Dispatchers.IO
        val code =
            """
            import kotlinx.coroutines.*

            suspend fun readFile() = withContext(Dispatchers.IO) { 1 }
            """.trimIndent()

        // compileAndLint uses a synthetic path without /commonMain/ or /commonTest/
        // so this confirms the path heuristic does not fire outside common sources
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports Dispatchers IO under commonMain source root`() {
        val uri = javaClass.getResource("/detekt-kmp-common/src/commonMain/kotlin/UsesIo.kt")!!
        val findings = rule.lint(Paths.get(uri.toURI()))
        assertThat(findings).hasSize(1)
        assertThat(findings.single().message).contains("[KMP_001]")
    }

    @Test
    fun `does not report when suppressed in commonMain file`() {
        val uri = javaClass.getResource(
            "/detekt-kmp-common-suppress/src/commonMain/kotlin/SuppressedIo.kt"
        )!!
        val findings = rule.lint(Paths.get(uri.toURI()))
        assertThat(findings).isEmpty()
    }
}
