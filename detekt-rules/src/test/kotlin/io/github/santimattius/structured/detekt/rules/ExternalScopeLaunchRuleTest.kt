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

class ExternalScopeLaunchRuleTest {

    private val rule = ExternalScopeLaunchRule(Config.empty)

    @Test
    fun `reports launch on external scope from suspend function`() {
        val code = """
            import kotlinx.coroutines.*
            
            class MyService(private val scope: CoroutineScope) {
                suspend fun process() {
                    scope.launch { println("work") }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("external scope")
        assertThat(findings[0].message).contains("[SCOPE_003]")
    }

    @Test
    fun `does not report launch in non-suspend function`() {
        val code = """
            import kotlinx.coroutines.*
            
            class MyService(private val scope: CoroutineScope) {
                fun fireAndForget() {
                    scope.launch { println("work") }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report coroutineScope launch`() {
        val code = """
            import kotlinx.coroutines.*
            
            suspend fun process() = coroutineScope {
                launch { println("work") }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }

    @Test
    fun `does not report viewModelScope`() {
        val code = """
            import kotlinx.coroutines.*
            
            class MyViewModel {
                private val viewModelScope = CoroutineScope(Dispatchers.Main)
                
                suspend fun process() {
                    viewModelScope.launch { println("work") }
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        // viewModelScope is in the allowed list
        assertThat(findings).isEmpty()
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
                suspend fun readInline(key: String): String? = connection.async().get(key).await()

                suspend fun readAssigned(key: String): String? {
                    val commands = connection.async()
                    return commands.get(key).await()
                }
            }
        """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
