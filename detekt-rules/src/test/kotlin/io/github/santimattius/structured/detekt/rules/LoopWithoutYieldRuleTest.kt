/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.detekt.rules

import dev.detekt.api.Config
import dev.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LoopWithoutYieldRuleTest {

    private val rule = LoopWithoutYieldRule(Config.empty)

    @Test
    fun `reports for loop without cooperation point in suspend function`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    println(item * 2)  // No yield, ensureActive, or delay
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("for")
        assertThat(findings[0].message).contains("cooperation point")
        assertThat(findings[0].message).contains("[CANCEL_001]")
    }

    @Test
    fun `reports while loop without cooperation point in suspend function`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun processItems() {
                var i = 0
                while (i < 100) {
                    println(i)
                    i++
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("while")
    }

    @Test
    fun `does not report loop with yield`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    yield()
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report loop with ensureActive`() {
        val code = """
            import kotlinx.coroutines.*
            import kotlin.coroutines.coroutineContext
            
            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    ensureActive()
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report loop with delay`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun processItems(items: List<Int>) {
                for (item in items) {
                    delay(10)
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports loop with only an uncalled local suspend fun containing delay (spike, #66 divergence)`() {
        // SPIKE: unlike the compiler checker (which prunes nested FirNamedFunction/
        // FirPropertyAccessor declarations, see #66), this rule's unpruned
        // collectDescendantsOfType<KtCallExpression>() walk over the whole loop body finds
        // `delay(1)` inside the *declared-but-never-called* local `helper()` and mistakes it
        // for a real cooperation point. `helper()` is dead code: `delay(1)` never executes
        // during loop iterations, so this while loop still busy-loops and SHOULD be reported.
        val code = """
            import kotlinx.coroutines.*

            suspend fun processItems() {
                var i = 0
                while (i < 100) {
                    suspend fun helper() {
                        delay(1)
                    }
                    println(i)
                    i++
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report loop when the local suspend fun cooperation point is actually called`() {
        // Negative counterpart to the spike above: `fetchData()` is now called on every
        // iteration, so `delay(1)` genuinely executes as a cooperation point. Pruning the
        // declaration must not suppress detection of the call site itself. The call is named
        // `fetchData` (matching `isSuspendCall`'s "fetch" naming heuristic) rather than `helper`,
        // since this rule's suspend-call detection is a naming heuristic, not real type
        // resolution — an arbitrarily-named call is not something this rule can recognize as a
        // cooperation point regardless of pruning.
        val code = """
            import kotlinx.coroutines.*

            suspend fun processItems() {
                var i = 0
                while (i < 100) {
                    suspend fun fetchData() {
                        delay(1)
                    }
                    fetchData()
                    println(i)
                    i++
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report loop in non-suspend function`() {
        val code = """
            fun processItems(items: List<Int>) {
                for (item in items) {
                    println(item * 2)
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
