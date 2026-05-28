package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SharedMutableStateInCoroutineRuleTest {

    private val rule = SharedMutableStateInCoroutineRule(Config.empty)

    @Test
    fun `reports parallel launch mutating shared list`() {
        val code = """
            import kotlinx.coroutines.*

            fun work() = coroutineScope {
                var results = mutableListOf<Int>()
                launch { results.add(1) }
                launch { results.add(2) }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isNotEmpty
        assertThat(findings[0].message).contains("[CONCUR_002]")
    }

    @Test
    fun `does not report awaitAll pattern`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun load(items: List<Int>) = coroutineScope {
                items.map { async { it * 2 } }.awaitAll()
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            @file:Suppress("SharedMutableStateInCoroutine")

            import kotlinx.coroutines.*

            suspend fun load(items: List<Int>) = coroutineScope {
                var results = mutableListOf<Int>()
                items.forEach { launch { results.add(it) } }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
