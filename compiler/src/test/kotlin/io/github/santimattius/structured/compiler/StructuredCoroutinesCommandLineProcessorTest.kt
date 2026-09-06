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

import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Task 1.5/1.6 (severity-enforcement, #68, ADR-3): [StructuredCoroutinesCommandLineProcessor]
 * bridges Gradle's `SubpluginOption`s into [CompilerConfiguration]. Before this class (and its
 * `META-INF/services` registration), [PluginConfiguration.OPTIONS_KEY] was never populated in a
 * real build, so DSL severities never reached the compiler at all (Ground truth in design.md).
 */
@OptIn(ExperimentalCompilerApi::class, CompilerConfiguration.Internals::class)
class StructuredCoroutinesCommandLineProcessorTest {

    @Test
    fun `pluginId matches the registered compiler plugin id`() {
        val processor = StructuredCoroutinesCommandLineProcessor()
        assertEquals("io.github.santimattius.structured-coroutines", processor.pluginId)
    }

    @Test
    fun `pluginOptions declares the 14 ScoroutinesRule option keys plus severityEnforcement`() {
        val processor = StructuredCoroutinesCommandLineProcessor()
        assertEquals(15, processor.pluginOptions.size)
        assertEquals(
            ScoroutinesRule.entries.map { it.optionKey }.toSet() + "severityEnforcement",
            processor.pluginOptions.map { it.optionName }.toSet(),
        )
    }

    @Test
    fun `severityEnforcement option is accepted and stored like any other option`() {
        val processor = StructuredCoroutinesCommandLineProcessor()
        val configuration = CompilerConfiguration()
        val option = processor.pluginOptions.first { it.optionName == "severityEnforcement" }

        processor.processOption(option, "strict", configuration)

        assertEquals(
            mapOf("severityEnforcement" to "strict"),
            configuration.get(PluginConfiguration.OPTIONS_KEY),
        )
    }

    @Test
    fun `processOption called twice with different keys accumulates both without clobbering`() {
        val processor = StructuredCoroutinesCommandLineProcessor()
        val configuration = CompilerConfiguration()
        val loopOption = processor.pluginOptions.first { it.optionName == "loopWithoutYield" }
        val suspendOption = processor.pluginOptions.first { it.optionName == "suspendInFinally" }

        processor.processOption(loopOption, "error", configuration)
        processor.processOption(suspendOption, "disabled", configuration)

        val stored = configuration.get(PluginConfiguration.OPTIONS_KEY)
        assertEquals(
            mapOf("loopWithoutYield" to "error", "suspendInFinally" to "disabled"),
            stored,
        )
    }

    @Test
    fun `processOption called again for the same key overwrites only that key`() {
        val processor = StructuredCoroutinesCommandLineProcessor()
        val configuration = CompilerConfiguration()
        val loopOption = processor.pluginOptions.first { it.optionName == "loopWithoutYield" }

        processor.processOption(loopOption, "warning", configuration)
        processor.processOption(loopOption, "error", configuration)

        assertEquals(
            mapOf("loopWithoutYield" to "error"),
            configuration.get(PluginConfiguration.OPTIONS_KEY),
        )
    }
}
