/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.lint.detectors

import com.android.tools.lint.checks.infrastructure.TestFiles
import com.android.tools.lint.checks.infrastructure.TestLintTask
import org.junit.Test

/**
 * Tests for [MisplacedTryCatchInScopeDetector] (EXCEPT_004).
 *
 * The shared `LintTestStubs` have no `Deferred`, `await`, `join`, `coroutineScope` or
 * `supervisorScope`, and unresolved calls never reach `visitMethodCall`. A self-contained local
 * stub of the `kotlinx.coroutines` surface used here is therefore declared in this file.
 */
class MisplacedTryCatchInScopeDetectorTest {

    private val coroutinesStub = """
        package kotlinx.coroutines
        interface CoroutineScope
        interface Job {
            suspend fun join()
        }
        interface Deferred<out T> : Job {
            suspend fun await(): T
        }
        fun CoroutineScope.launch(block: suspend CoroutineScope.() -> Unit): Job = error("stub")
        fun <T> CoroutineScope.async(block: suspend CoroutineScope.() -> T): Deferred<T> = error("stub")
        suspend fun <T> coroutineScope(block: suspend CoroutineScope.() -> T): T = error("stub")
        suspend fun <T> supervisorScope(block: suspend CoroutineScope.() -> T): T = error("stub")
        suspend fun <T> awaitAll(vararg deferreds: Deferred<T>): List<T> = error("stub")
        suspend fun <T> Collection<Deferred<T>>.awaitAll(): List<T> = error("stub")
        suspend fun joinAll(vararg jobs: Job): Unit = error("stub")
    """.trimIndent()

    private fun run(body: String) = TestLintTask.lint()
        .files(
            TestFiles.kotlin(coroutinesStub).indented(),
            TestFiles.kotlin(
                """
                package test

                import kotlinx.coroutines.*

                suspend fun compute(): Int = 1
                suspend fun work() {}
                """.trimIndent() + "\n" + body.trimIndent()
            ).indented(),
        )
        .issues(MisplacedTryCatchInScopeDetector.ISSUE)
        .allowMissingSdk()
        .run()

    @Test
    fun `reports await of async created in the same coroutineScope`() {
        run("""
suspend fun bad(): Int = coroutineScope {
    val d = async { compute() }
    try {
        d.await()
    } catch (e: Exception) {
        0
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports join of launch created in the same coroutineScope`() {
        run("""
suspend fun bad(): Unit = coroutineScope {
    val j = launch { work() }
    try {
        j.join()
    } catch (e: Exception) {
        println(e)
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports await used in a derived expression`() {
        run("""
suspend fun bad(): Int = coroutineScope {
    val d = async { compute() }
    try {
        val r = d.await()
        r + 1
    } catch (e: Exception) {
        0
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports await used as a call argument`() {
        run("""
suspend fun bad(): Unit = coroutineScope {
    val d = async { compute() }
    try {
        println(d.await())
    } catch (e: Exception) {
        println(e)
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports await through a simple alias`() {
        run("""
suspend fun bad(): Int = coroutineScope {
    val d = async { compute() }
    val e = d
    try {
        e.await()
    } catch (ex: Exception) {
        0
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports inline async await`() {
        run("""
suspend fun bad(): Int = coroutineScope {
    try {
        async { compute() }.await()
    } catch (e: Exception) {
        0
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports awaitAll with tracked children`() {
        run("""
suspend fun bad(): Unit = coroutineScope {
    val a = async { compute() }
    val b = async { compute() }
    try {
        awaitAll(a, b)
    } catch (e: Exception) {
        println(e)
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports awaitAll on a collection of tracked children`() {
        run("""
suspend fun bad(): Unit = coroutineScope {
    val d = async { compute() }
    try {
        listOf(d).awaitAll()
    } catch (e: Exception) {
        println(e)
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports exactly one warning for one misplaced try`() {
        run("""
suspend fun bad(): Int = coroutineScope {
    val d = async { compute() }
    val e = async { compute() }
    try {
        d.await() + e.await()
    } catch (ex: Exception) {
        0
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `reports a nested coroutineScope once`() {
        run("""
suspend fun bad(): Int = coroutineScope {
    coroutineScope {
        val d = async { compute() }
        try {
            d.await()
        } catch (e: Exception) {
            0
        }
    }
}
        """).expectWarningCount(1)
    }

    @Test
    fun `does not report try wrapping the whole coroutineScope`() {
        run("""
suspend fun good(): Int =
    try {
        coroutineScope {
            val d = async { compute() }
            d.await()
        }
    } catch (e: Exception) {
        0
    }
        """).expectClean()
    }

    @Test
    fun `does not report inside supervisorScope`() {
        run("""
suspend fun good(): Int = supervisorScope {
    val d = async { compute() }
    try {
        d.await()
    } catch (e: Exception) {
        0
    }
}
        """).expectClean()
    }

    @Test
    fun `does not report exception caught inside the async block`() {
        run("""
suspend fun good(): Int = coroutineScope {
    val d = async {
        try {
            compute()
        } catch (e: Exception) {
            0
        }
    }
    d.await()
}
        """).expectClean()
    }

    @Test
    fun `does not report Deferred received as a parameter`() {
        run("""
suspend fun good(d: Deferred<Int>): Int = coroutineScope {
    try {
        d.await()
    } catch (e: Exception) {
        0
    }
}
        """).expectClean()
    }

    @Test
    fun `does not report try with only finally`() {
        run("""
suspend fun good(): Int = coroutineScope {
    val d = async { compute() }
    try {
        d.await()
    } finally {
        println("done")
    }
}
        """).expectClean()
    }

    @Test
    fun `does not report try without await of a child`() {
        run("""
suspend fun good(): Int = coroutineScope {
    val d = async { compute() }
    try {
        compute()
    } catch (e: Exception) {
        0
    }
}
        """).expectClean()
    }
}
