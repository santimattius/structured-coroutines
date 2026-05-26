package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ThreadLocalNotPropagatedRuleTest {

    private val rule = ThreadLocalNotPropagatedRule(Config.empty)

    @Test
    fun `reports withContext IO without MDCContext when MDC is used`() {
        val code = """
            import kotlinx.coroutines.*
            import org.slf4j.MDC

            suspend fun load() {
                MDC.put("trace", "1")
                withContext(Dispatchers.IO) { MDC.get("trace") }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[BACKEND_002]")
    }

    @Test
    fun `does not report when MDCContext is present`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlinx.coroutines.slf4j.MDCContext
            import org.slf4j.MDC

            suspend fun load() {
                MDC.put("trace", "1")
                withContext(Dispatchers.IO + MDCContext()) { MDC.get("trace") }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report without MDC imports`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun load() {
                withContext(Dispatchers.IO) { 42 }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
