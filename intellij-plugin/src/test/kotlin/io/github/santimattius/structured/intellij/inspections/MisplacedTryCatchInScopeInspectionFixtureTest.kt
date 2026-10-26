/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.inspections

import io.github.santimattius.structured.intellij.fixture.InspectionFixtureTestCase

/**
 * Behavioural PSI fixture tests for [MisplacedTryCatchInScopeInspection] (EXCEPT_004).
 *
 * Cases mirror the Detekt (`MisplacedTryCatchInScopeRuleTest`) and Lint
 * (`MisplacedTryCatchInScopeDetectorTest`) tests for the same rule.
 */
class MisplacedTryCatchInScopeInspectionFixtureTest : InspectionFixtureTestCase() {

    private fun count(code: String): Int =
        highlights(MisplacedTryCatchInScopeInspection(), code.trimIndent()).size

    fun `test reports await in try catch of async created in same coroutineScope`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test reports join of launch created in same coroutineScope`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test reports await used in a derived expression`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test reports await through a simple alias`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test reports inline async await`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test() = coroutineScope {
                try {
                    async<Int> { error("boom") }.await()
                } catch (e: Exception) {
                    0
                }
            }
        """
        assertEquals(1, count(code))
    }

    fun `test reports awaitAll with tracked children as arguments`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test reports awaitAll extension on a list of tracked children`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test reports exactly one finding for one misplaced try`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test reports nested coroutineScope once`() {
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
        """
        assertEquals(1, count(code))
    }

    fun `test does not report try catch wrapping the whole coroutineScope`() {
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
        """
        assertEquals(0, count(code))
    }

    fun `test does not report inside supervisorScope`() {
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
        """
        assertEquals(0, count(code))
    }

    fun `test does not report when exception is caught inside the async block`() {
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
        """
        assertEquals(0, count(code))
    }

    fun `test does not report when Deferred is received as a parameter`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(d: Deferred<Int>): Int = coroutineScope {
                try {
                    d.await()
                } catch (e: Exception) {
                    0
                }
            }
        """
        assertEquals(0, count(code))
    }

    fun `test does not report try with only finally`() {
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
        """
        assertEquals(0, count(code))
    }

    fun `test does not report try without await of a child`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(): Int = coroutineScope {
                val d = async { 1 }
                try {
                    println("no await")
                } catch (e: Exception) {
                    0
                }
                d.await()
            }
        """
        assertEquals(0, count(code))
    }

    fun `test does not report file without kotlinx coroutines import`() {
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
        """
        assertEquals(0, count(code))
    }

    fun `test highlights only the try keyword`() {
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
        val found = highlights(MisplacedTryCatchInScopeInspection(), code)
        assertEquals(1, found.size)
        assertEquals("try", code.substring(found[0].startOffset, found[0].endOffset))
    }
}
