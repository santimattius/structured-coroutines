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

class UnusedDeferredRuleTest {

    private val rule = UnusedDeferredRule(Config.empty)

    @Test
    fun `reports async when result not awaited`() {
        val code = """
            import kotlinx.coroutines.*
            
            fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("SCOPE_002")
    }

    @Test
    fun `does not report async when awaited`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                d.await()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async when result used in awaitAll`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun test(scope: CoroutineScope) {
                val a = scope.async { 1 }
                val b = scope.async { 2 }
                awaitAll(a, b)
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async when List of Deferred is used with awaitAll extension`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun test(scope: CoroutineScope, xs: List<Int>) {
                val defs = xs.map { scope.async { it * 2 } }
                defs.awaitAll()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async when map async doSomething and defs awaitAll`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun doSomething(x: Int): Int = x
            
            suspend fun test(scope: CoroutineScope, xs: List<Int>) {
                val defs = xs.map { scope.async { doSomething(it) } }
                defs.awaitAll()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async when coroutineScope map async and defs awaitAll`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun doSomething(x: Int): Int = x
            
            suspend fun test(xs: List<Int>) = coroutineScope {
                val defs = xs.map { async { doSomething(it) } }
                defs.awaitAll()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report launch`() {
        val code = """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope) {
                scope.launch { }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async awaited through a chain in a val initializer`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope, xs: List<Int>) {
                val r = xs.map { scope.async { it } }.awaitAll()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async awaited directly in a val initializer`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val r = scope.async { 1 }.await()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async awaited inside a launch assigned to a val`() {
        val code = """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope, xs: List<Int>) {
                val job = scope.launch { xs.map { async { it } }.awaitAll() }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async held in a val that is the awaited lambda result`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope, xs: List<Int>) {
                xs.map { val d = scope.async { it }; d }.awaitAll()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async awaited through an alias`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                val e = d
                e.await()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report async awaited inside a collection`() {
        val code = """
            import kotlinx.coroutines.*

            suspend fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                listOf(d).awaitAll()
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `reports async put in a collection that is never awaited`() {
        val code = """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope) {
                val d = scope.async { 1 }
                listOf(d)
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `reports async returned from a lambda whose result is never awaited`() {
        val code = """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope, xs: List<Int>) {
                val defs = xs.map { val d = scope.async { it }; d }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `reports async inside a launch assigned to a val when never awaited`() {
        val code = """
            import kotlinx.coroutines.*

            fun test(scope: CoroutineScope) {
                val job = scope.launch { async { 1 } }
            }
        """.trimIndent()
        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
    }

    @Test
    fun `does not report non-coroutine async method without arguments (issue 155)`() {
        val code = """
            import java.util.concurrent.CompletionStage
            import kotlinx.coroutines.future.await

            interface AsyncCommands {
                fun get(key: String): CompletionStage<String?>
            }

            interface Connection {
                fun async(): AsyncCommands
            }

            class KeyValueStore(private val connection: Connection) {
                suspend fun readBoth(first: String, second: String): List<String?> {
                    val commands = connection.async()
                    return listOf(read(commands, first), read(commands, second))
                }

                private suspend fun read(commands: AsyncCommands, key: String): String? = commands.get(key).await()
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
