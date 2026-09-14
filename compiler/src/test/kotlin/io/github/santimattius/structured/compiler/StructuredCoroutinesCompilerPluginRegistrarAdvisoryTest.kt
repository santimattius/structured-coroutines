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

import org.jetbrains.kotlin.cli.common.diagnosticsCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.KtDiagnosticWithoutSource
import org.jetbrains.kotlin.diagnostics.Severity
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Task 3.7/3.8 (severity-enforcement, #68, ADR-7): [StructuredCoroutinesCompilerPluginRegistrar]
 * MUST emit exactly one [Severity.WARNING] diagnostic per compilation, naming every rule in
 * [PluginConfiguration.deferredTightenings], and MUST emit nothing when the list is empty
 * (relaxations only, or nothing configured).
 */
@OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class)
class StructuredCoroutinesCompilerPluginRegistrarAdvisoryTest {

    /**
     * The registrar reports via [CompilerConfiguration.report] (org.jetbrains.kotlin.cli), which
     * writes into the compilation's [org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector]
     * (auto-created on first access), not directly into a [org.jetbrains.kotlin.cli.common.messages.MessageCollector] —
     * the latter is only populated when the full compiler pipeline later flushes the collector.
     */
    private fun registerAndCollect(options: Map<String, String>): List<KtDiagnosticWithoutSource> {
        val configuration = CompilerConfiguration()
        configuration.put(PluginConfiguration.OPTIONS_KEY, options)
        // A bare CompilerConfiguration() has no diagnosticsCollector pre-installed (only the real
        // CLI pipeline's configuration-building helpers install one); install it explicitly so
        // CompilerConfiguration.report(...) has somewhere to write.
        configuration.diagnosticsCollector = DiagnosticsCollectorImpl()

        val registrar = StructuredCoroutinesCompilerPluginRegistrar()
        with(registrar) {
            CompilerPluginRegistrar.ExtensionStorage().registerExtensions(configuration)
        }
        return configuration.diagnosticsCollector.diagnostics.filterIsInstance<KtDiagnosticWithoutSource>()
    }

    @Test
    fun `no advisory is emitted when nothing is configured`() {
        val reported = registerAndCollect(emptyMap())
        assertTrue(reported.isEmpty(), "expected no messages, got: ${reported.map { it.message }}")
    }

    @Test
    fun `no advisory is emitted for relaxations only`() {
        val reported = registerAndCollect(mapOf("unusedDeferred" to "disabled", "globalScopeUsage" to "warning"))
        assertTrue(reported.isEmpty(), "expected no messages, got: ${reported.map { it.message }}")
    }

    @Test
    fun `exactly one WARNING advisory is emitted per compilation when a tightening is deferred`() {
        val reported = registerAndCollect(mapOf("loopWithoutYield" to "error"))
        val warnings = reported.filter { it.severity == Severity.WARNING }
        assertEquals(1, warnings.size, "expected exactly one WARNING message, got: ${reported.map { it.message }}")
        val message = warnings.single().message
        assertTrue("loopWithoutYield" in message, "advisory must name the rule, got:\n$message")
        assertTrue(SeverityGracePeriod.ENFORCING_VERSION in message, "advisory must name ENFORCING_VERSION, got:\n$message")
    }

    @Test
    fun `advisory names every deferred rule when multiple tightenings are configured`() {
        val reported = registerAndCollect(mapOf("loopWithoutYield" to "error", "suspendInFinally" to "error"))
        val warnings = reported.filter { it.severity == Severity.WARNING }
        assertTrue(warnings.isNotEmpty(), "expected at least one WARNING message, got: ${reported.map { it.message }}")
        val combined = warnings.joinToString("\n") { it.message }
        assertTrue("loopWithoutYield" in combined, "advisory must name loopWithoutYield, got:\n$combined")
        assertTrue("suspendInFinally" in combined, "advisory must name suspendInFinally, got:\n$combined")
    }

    @Test
    fun `no advisory is emitted when severityEnforcement is strict, even with a tightened rule`() {
        val reported = registerAndCollect(mapOf("loopWithoutYield" to "error", "severityEnforcement" to "strict"))
        assertTrue(reported.isEmpty(), "expected no messages under strict policy, got: ${reported.map { it.message }}")
    }
}
