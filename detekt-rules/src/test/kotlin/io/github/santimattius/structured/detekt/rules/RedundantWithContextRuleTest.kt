package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RedundantWithContextRuleTest {

    private val rule = RedundantWithContextRule(Config.empty)

    @Test
    fun `reports nested withContext with same dispatcher ref`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun load(io: CoroutineDispatcher) = withContext(io) {
                withContext(io) { 42 }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[CONCUR_004]")
    }

    @Test
    fun `does not report different dispatchers`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun load() = withContext(Dispatchers.IO) {
                withContext(Dispatchers.Default) { 42 }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            @file:Suppress("RedundantWithContext")

            import kotlinx.coroutines.*

            suspend fun load(io: CoroutineDispatcher) = withContext(io) {
                withContext(io) { 42 }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
