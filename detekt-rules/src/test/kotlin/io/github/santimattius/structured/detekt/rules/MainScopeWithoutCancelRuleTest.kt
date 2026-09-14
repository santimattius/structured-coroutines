package io.github.santimattius.structured.detekt.rules

import dev.detekt.api.Config
import dev.detekt.test.lint
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

        val findings = rule.lint(code)
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

        assertThat(rule.lint(code)).isEmpty()
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

        assertThat(rule.lint(code)).isEmpty()
    }
}
