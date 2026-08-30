/**
 * Copyright 2026 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 3.7/3.8 (severity-enforcement, #68, ADR-7): [StructuredCoroutinesCompilerPluginRegistrar]
 * MUST emit exactly one [MessageCollector.WARNING][CompilerMessageSeverity.WARNING] per
 * compilation, naming every rule in [PluginConfiguration.deferredTightenings], and MUST emit
 * nothing when the list is empty (relaxations only, or nothing configured).
 */
@OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class)
class StructuredCoroutinesCompilerPluginRegistrarAdvisoryTest {

    private class RecordingMessageCollector : MessageCollector {
        val reported = mutableListOf<Pair<CompilerMessageSeverity, String>>()
        override fun clear() {
            reported.clear()
        }

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
            reported.add(severity to message)
        }

        override fun hasErrors(): Boolean = reported.any { it.first.isError }
    }

    private fun registerAndCollect(options: Map<String, String>): RecordingMessageCollector {
        val configuration = CompilerConfiguration()
        configuration.put(PluginConfiguration.OPTIONS_KEY, options)
        val collector = RecordingMessageCollector()
        configuration.put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, collector)

        val registrar = StructuredCoroutinesCompilerPluginRegistrar()
        with(registrar) {
            CompilerPluginRegistrar.ExtensionStorage().registerExtensions(configuration)
        }
        return collector
    }

    @Test
    fun `no advisory is emitted when nothing is configured`() {
        val collector = registerAndCollect(emptyMap())
        assertTrue(collector.reported.isEmpty(), "expected no messages, got: ${collector.reported}")
    }

    @Test
    fun `no advisory is emitted for relaxations only`() {
        val collector = registerAndCollect(mapOf("unusedDeferred" to "disabled", "globalScopeUsage" to "warning"))
        assertTrue(collector.reported.isEmpty(), "expected no messages, got: ${collector.reported}")
    }

    @Test
    fun `exactly one WARNING advisory is emitted per compilation when a tightening is deferred`() {
        val collector = registerAndCollect(mapOf("loopWithoutYield" to "error"))
        val warnings = collector.reported.filter { it.first == CompilerMessageSeverity.WARNING }
        assertEquals(1, warnings.size, "expected exactly one WARNING message, got: ${collector.reported}")
        val message = warnings.single().second
        assertTrue("loopWithoutYield" in message, "advisory must name the rule, got:\n$message")
        assertTrue(SeverityGracePeriod.ENFORCING_VERSION in message, "advisory must name ENFORCING_VERSION, got:\n$message")
    }

    @Test
    fun `advisory names every deferred rule when multiple tightenings are configured`() {
        val collector = registerAndCollect(mapOf("loopWithoutYield" to "error", "suspendInFinally" to "error"))
        val warnings = collector.reported.filter { it.first == CompilerMessageSeverity.WARNING }
        assertTrue(warnings.isNotEmpty(), "expected at least one WARNING message, got: ${collector.reported}")
        val combined = warnings.joinToString("\n") { it.second }
        assertTrue("loopWithoutYield" in combined, "advisory must name loopWithoutYield, got:\n$combined")
        assertTrue("suspendInFinally" in combined, "advisory must name suspendInFinally, got:\n$combined")
    }

    @Test
    fun `no advisory is emitted when severityEnforcement is strict, even with a tightened rule`() {
        val collector = registerAndCollect(mapOf("loopWithoutYield" to "error", "severityEnforcement" to "strict"))
        assertTrue(collector.reported.isEmpty(), "expected no messages under strict policy, got: ${collector.reported}")
    }
}
