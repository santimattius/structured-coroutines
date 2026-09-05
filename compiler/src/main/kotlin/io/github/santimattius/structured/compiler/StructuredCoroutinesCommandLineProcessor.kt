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

import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * CLI bridge that carries every `-P plugin:<pluginId>:<key>=<value>` option emitted by
 * [io.github.santimattius.structured.gradle.StructuredCoroutinesGradlePlugin] into
 * [CompilerConfiguration] (#68, ADR-3).
 *
 * Registered via `META-INF/services/org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor`.
 * Deleting that service file alone restores today's behavior (options never reach the
 * compiler) — the documented rollback kill switch for this bridge.
 *
 * [pluginId] MUST equal the id declared by `StructuredCoroutinesCompilerPluginRegistrar` and
 * `StructuredCoroutinesGradlePlugin.COMPILER_PLUGIN_ID`, or the Kotlin compiler will not route
 * options emitted for that id to this processor.
 */
@OptIn(ExperimentalCompilerApi::class)
class StructuredCoroutinesCommandLineProcessor : CommandLineProcessor {

    override val pluginId: String = "io.github.santimattius.structured-coroutines"

    override val pluginOptions: Collection<AbstractCliOption> = ScoroutinesRule.entries.map { rule ->
        CliOption(
            optionName = rule.optionKey,
            valueDescription = "<error|warning|disabled>",
            description = "Severity for the ${rule.optionKey} rule",
            required = false,
            allowMultipleOccurrences = false,
        )
    } + CliOption(
        optionName = "severityEnforcement",
        valueDescription = "<grace|strict>",
        description = "Grace-period enforcement policy for severity tightening (#68, ADR-7)",
        required = false,
        allowMultipleOccurrences = false,
    )

    /**
     * Read-modify-write of the single [PluginConfiguration.OPTIONS_KEY] map (ADR-3), honoring
     * the [CompilerConfigurationKey][org.jetbrains.kotlin.config.CompilerConfigurationKey]
     * reference-equality trap documented at `PluginConfiguration.kt:24-26`: every call must
     * reuse that exact static key instance, never create a new one.
     */
    @OptIn(CompilerConfiguration.Internals::class)
    override fun processOption(
        option: AbstractCliOption,
        value: String,
        configuration: CompilerConfiguration,
    ) {
        val current = configuration.get(PluginConfiguration.OPTIONS_KEY) ?: emptyMap()
        configuration.put(PluginConfiguration.OPTIONS_KEY, current + (option.optionName to value))
    }
}
