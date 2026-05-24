package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MainScopeWithoutCancelRuleTest {

    private val rule = MainScopeWithoutCancelRule(Config.empty)

    @Test
    fun `reports MainScope without cancel in cleanup`() {
        val code = """
            import kotlinx.coroutines.*

            class Presenter {
                private val scope = kotlinx.coroutines.MainScope()
                fun onDestroy() { }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[KMP_003]")
    }

    @Test
    fun `does not report when scope cancel is called`() {
        val code = """
            import kotlinx.coroutines.*

            class Presenter {
                private val scope = MainScope()
                fun onDestroy() { scope.cancel() }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            @file:Suppress("MainScopeWithoutCancel")

            import kotlinx.coroutines.*

            class Presenter {
                private val scope = MainScope()
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
