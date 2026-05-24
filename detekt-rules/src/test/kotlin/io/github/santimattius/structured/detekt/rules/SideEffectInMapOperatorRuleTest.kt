package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SideEffectInMapOperatorRuleTest {

    private val rule = SideEffectInMapOperatorRule(Config.empty)

    @Test
    fun `reports side effect before return in map`() {
        val code = """
            import kotlinx.coroutines.flow.*

            fun pipeline(flow: Flow<Int>) = flow.map { item ->
                println("side effect")
                item * 2
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[FLOW_008]")
    }

    @Test
    fun `does not report pure map`() {
        val code = """
            import kotlinx.coroutines.flow.*

            fun pipeline(flow: Flow<Int>) = flow.map { it * 2 }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            @file:Suppress("SideEffectInMapOperator")

            import kotlinx.coroutines.flow.*

            fun pipeline(flow: Flow<Int>) = flow.map { item ->
                println("side effect")
                item * 2
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
