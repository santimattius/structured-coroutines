package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StateInWithEagerlyStrategyRuleTest {

    private val rule = StateInWithEagerlyStrategyRule(Config.empty)

    @Test
    fun `reports Eagerly with viewModelScope`() {
        val code = """
            import kotlinx.coroutines.flow.*
            import kotlinx.coroutines.*

            class Vm(private val viewModelScope: CoroutineScope) {
                val state = emptyFlow<Int>().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[FLOW_006]")
    }

    @Test
    fun `does not report WhileSubscribed with viewModelScope`() {
        val code = """
            import kotlinx.coroutines.flow.*
            import kotlinx.coroutines.*

            class Vm(private val viewModelScope: CoroutineScope) {
                val state = emptyFlow<Int>().stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5_000),
                    0,
                )
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            @file:Suppress("StateInWithEagerlyStrategy")

            import kotlinx.coroutines.flow.*
            import kotlinx.coroutines.*

            class Vm(private val viewModelScope: CoroutineScope) {
                val state = emptyFlow<Int>().stateIn(viewModelScope, SharingStarted.Eagerly, 0)
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
