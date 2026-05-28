package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import io.gitlab.arturbosch.detekt.test.lint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Paths

class RunBlockingInCommonMainRuleTest {

    private val rule = RunBlockingInCommonMainRule(Config.empty)

    @Test
    fun `does not report runBlocking outside common source roots`() {
        val code = """
            import kotlinx.coroutines.runBlocking

            fun load() = runBlocking { 1 }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `reports runBlocking under commonMain`() {
        val uri = javaClass.getResource(
            "/detekt-kmp-common-runblocking/src/commonMain/kotlin/UsesRunBlocking.kt",
        )!!
        val findings = rule.lint(Paths.get(uri.toURI()))
        assertThat(findings).hasSize(1)
        assertThat(findings.single().message).contains("[KMP_002]")
    }

    @Test
    fun `does not report when suppressed in commonMain`() {
        val code = """
            @file:Suppress("RunBlockingInCommonMain")

            import kotlinx.coroutines.runBlocking

            fun load() = runBlocking { 1 }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
