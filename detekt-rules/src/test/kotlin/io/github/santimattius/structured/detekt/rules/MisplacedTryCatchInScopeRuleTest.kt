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

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MisplacedTryCatchInScopeRuleTest {

    private val rule = MisplacedTryCatchInScopeRule(Config.empty)

    // ---- Positive cases ----

    @Test
    fun `reports await in try catch of async created in same coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(): Int = coroutineScope {
                val d = async<Int> { error("boom") }
                try {
                    d.await()
                } catch (e: Exception) {
                    0
                }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("EXCEPT_004")
    }

    @Test
    fun `reports join of launch created in same coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                val j = launch { error("boom") }
                try {
                    j.join()
                } catch (e: Exception) {
                    println("failed")
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    @Test
    fun `reports await used in a derived expression`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                val d = async<Int> { error("boom") }
                try {
                    val r = d.await()
                    println(d.await())
                    r
                } catch (e: Exception) {
                    0
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    @Test
    fun `reports await through a simple alias`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                val d = async<Int> { error("boom") }
                val e = d
                try {
                    e.await()
                } catch (ex: Exception) {
                    0
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    @Test
    fun `reports inline async await`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                try {
                    async<Int> { error("boom") }.await()
                } catch (e: Exception) {
                    0
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    @Test
    fun `reports awaitAll with tracked children as arguments`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                val a = async { 1 }
                val b = async { 2 }
                try {
                    awaitAll(a, b)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    @Test
    fun `reports awaitAll extension on a list of tracked children`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                val d = async { 1 }
                try {
                    listOf(d).awaitAll()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    @Test
    fun `reports exactly one finding for one misplaced try`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                val a = async { 1 }
                val b = async { 2 }
                try {
                    a.await()
                    b.await()
                } catch (e: Exception) {
                    0
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    @Test
    fun `reports nested coroutineScope once`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                coroutineScope {
                    val d = async<Int> { error("boom") }
                    try {
                        d.await()
                    } catch (e: Exception) {
                        0
                    }
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).hasSize(1)
    }

    // ---- Negative cases ----

    @Test
    fun `does not report try catch wrapping the whole coroutineScope`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(): Int =
                try {
                    coroutineScope {
                        val d = async<Int> { error("boom") }
                        d.await()
                    }
                } catch (e: Exception) {
                    0
                }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report inside supervisorScope`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(): Int = supervisorScope {
                val d = async<Int> { error("boom") }
                try {
                    d.await()
                } catch (e: Exception) {
                    0
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when exception is caught inside the async block`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(): Int = coroutineScope {
                async {
                    try {
                        error("boom")
                    } catch (e: Exception) {
                        0
                    }
                }.await()
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report when Deferred is received as a parameter`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(d: Deferred<Int>): Int = coroutineScope {
                try {
                    d.await()
                } catch (e: Exception) {
                    0
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report try with only finally`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(): Int = coroutineScope {
                val d = async { 1 }
                try {
                    d.await()
                } finally {
                    println("done")
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).isEmpty()
    }

    @Test
    fun `does not report file without kotlinx coroutines import`() {
        val code = """
            fun coroutineScope(block: () -> Int): Int = block()
            fun async(block: () -> Int): Any = Any()
            fun Any.await(): Int = 0

            fun test(): Int = coroutineScope {
                val d = async { 1 }
                try {
                    d.await()
                } catch (e: Exception) {
                    0
                }
            }
        """.trimIndent()
        assertThat(rule.compileAndLint(code)).isEmpty()
    }
}
