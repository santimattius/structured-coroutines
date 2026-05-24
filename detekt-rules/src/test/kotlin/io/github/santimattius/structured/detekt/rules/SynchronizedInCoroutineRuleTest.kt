package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SynchronizedInCoroutineRuleTest {

    private val rule = SynchronizedInCoroutineRule(Config.empty)

    @Test
    fun `reports synchronized inside suspend function`() {
        val code = """
            import kotlinx.coroutines.sync.Mutex

            suspend fun increment(lock: Any, counter: Int): Int {
                synchronized(lock) { return counter + 1 }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[CONCUR_001]")
    }

    @Test
    fun `does not report mutex withLock instead of synchronized`() {
        val code = """
            import kotlinx.coroutines.sync.Mutex
            import kotlinx.coroutines.sync.withLock

            suspend fun increment(mutex: Mutex, counter: Int): Int {
                return mutex.withLock { counter + 1 }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when suppressed`() {
        val code = """
            @file:Suppress("SynchronizedInCoroutine")

            import kotlinx.coroutines.*

            suspend fun work(lock: Any) {
                synchronized(lock) { }
            }
        """.trimIndent()

        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
