package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BlockingCallInCoroutineBackendRuleTest {

    private val rule = BlockingCallInCoroutineBackendRule(Config.empty)

    @Test
    fun `reports Thread sleep in suspend without IO context`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun handler() {
                Thread.sleep(1000)
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[BACKEND_001]")
    }

    @Test
    fun `does not report blocking work inside withContext IO`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun handler() {
                withContext(Dispatchers.IO) {
                    Thread.sleep(1000)
                }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            @file:Suppress("BlockingCallInCoroutineBackend")

            import kotlinx.coroutines.*

            suspend fun handler() {
                Thread.sleep(1000)
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
