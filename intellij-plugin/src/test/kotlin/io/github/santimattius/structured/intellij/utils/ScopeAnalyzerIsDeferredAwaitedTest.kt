/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.intellij.utils

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtCallExpression

/**
 * PSI fixture tests for [ScopeAnalyzer.isDeferredAwaited], which backs the SCOPE_002
 * (`AsyncWithoutAwaitInspection`) inspection.
 *
 * Not covered yet: aliases wrapped in a cast, `!!`, `?:` or `if`, and `select { d.onAwait { } }`.
 * The compiler checker recognizes them (UNUSED_DEFERRED) but this PSI port does not.
 */
class ScopeAnalyzerIsDeferredAwaitedTest : BasePlatformTestCase() {

    /** Result of [ScopeAnalyzer.isDeferredAwaited] for each `async` call in [code], in source order. */
    private fun awaited(code: String): List<Boolean> {
        val file = myFixture.configureByText("Test.kt", code.trimIndent())
        return PsiTreeUtil.findChildrenOfType(file, KtCallExpression::class.java)
            .filter { it.calleeExpression?.text == "async" }
            .map { ScopeAnalyzer.isDeferredAwaited(it) }
    }

    private fun assertAwaited(code: String) = assertEquals(listOf(true), awaited(code))

    private fun assertNotAwaited(code: String) = assertEquals(listOf(false), awaited(code))

    fun `test treats direct await on a variable as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                d.await()
            }
            """,
        )
    }

    fun `test treats inline await on the async call as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                scope.async { 1 }.await()
            }
            """,
        )
    }

    fun `test treats await result assigned to a variable as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val r = scope.async { 1 }.await()
                println(r)
            }
            """,
        )
    }

    fun `test treats awaitAll on a mapped list variable as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope, xs: List<Int>) {
                val defs = xs.map { scope.async { it } }
                defs.awaitAll()
            }
            """,
        )
    }

    fun `test treats chained awaitAll without an intermediate variable as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope, xs: List<Int>) {
                xs.map { scope.async { it } }.awaitAll()
            }
            """,
        )
    }

    fun `test treats awaitAll with variables as arguments as awaited`() {
        assertEquals(listOf(true, true), awaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val a = scope.async { 1 }
                val b = scope.async { 2 }
                awaitAll(a, b)
            }
            """,
        ))
    }

    fun `test treats await through a simple alias as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                val e = d
                e.await()
            }
            """,
        )
    }

    fun `test treats await inside a nested block as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope, flag: Boolean) {
                val d = scope.async { 1 }
                if (flag) {
                    d.await()
                }
            }
            """,
        )
    }

    fun `test treats await through a smart cast alias as awaited`() {
        assertAwaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val d: Any = scope.async { 1 }
                if (d is Deferred<*>) {
                    val e = d
                    e.await()
                }
            }
            """,
        )
    }

    fun `test reports async result that is never used`() {
        assertNotAwaited(
            """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope) {
                scope.async { 1 }
            }
            """,
        )
    }

    fun `test reports variable that is never awaited`() {
        assertNotAwaited(
            """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                println(d)
            }
            """,
        )
    }

    fun `test reports await on a different variable`() {
        assertEquals(listOf(false, true), awaited(
            """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                val other = scope.async { 2 }
                other.await()
            }
            """,
        ))
    }

    fun `test reports list of async never awaited`() {
        assertNotAwaited(
            """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope, xs: List<Int>) {
                val defs = xs.map { scope.async { it } }
                println(defs.size)
            }
            """,
        )
    }
}
