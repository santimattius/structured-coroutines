/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package io.github.santimattius.structured.detekt.rules

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.compileAndLint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChannelFlowVsCallbackFlowRuleTest {

    private val rule = ChannelFlowVsCallbackFlowRule(Config.empty)

    @Test
    fun `reports channelFlow without awaitClose`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            fun sensorFlow(): Flow<Int> = channelFlow {
                trySend(1)
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("[INTEROP_003]")
    }

    @Test
    fun `reports callbackFlow with only internal coroutine emissions`() {
        val code =
            """
            import kotlinx.coroutines.*
            import kotlinx.coroutines.flow.*

            fun ticks(): Flow<Int> = callbackFlow {
                launch {
                    repeat(3) { send(it) }
                }
                awaitClose { }
            }
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).hasSize(1)
        assertThat(findings[0].message).contains("channelFlow")
    }

    @Test
    fun `does not report callbackFlow with external registration and awaitClose`() {
        val code =
            """
            import kotlinx.coroutines.flow.*

            fun sensorFlow(sensor: Any, cb: Any): Flow<Int> = callbackFlow {
                register(sensor, cb)
                awaitClose { unregister(sensor, cb) }
            }

            fun register(sensor: Any, cb: Any) {}
            fun unregister(sensor: Any, cb: Any) {}
            """.trimIndent()

        val findings = rule.compileAndLint(code)
        assertThat(findings).isEmpty()
    }
}
