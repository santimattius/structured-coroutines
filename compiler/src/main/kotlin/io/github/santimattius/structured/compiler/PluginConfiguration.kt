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

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey
import org.jetbrains.kotlin.diagnostics.Severity

/**
 * Configuration for the Structured Coroutines compiler plugin.
 *
 * Options are stored in [CompilerConfiguration] as a single [Map]<[String],[String]> under
 * [OPTIONS_KEY]. A [CommandLineProcessor] (or a test) populates that map before constructing
 * this class. Values are `"error"` or `"warning"` (case-insensitive); anything else falls
 * back to the default severity for that rule.
 */
class PluginConfiguration(configuration: CompilerConfiguration) {

    companion object {
        /**
         * Single static key used to store all plugin option key→value pairs in
         * [CompilerConfiguration]. Using one key avoids the [CompilerConfigurationKey]
         * reference-equality trap (the class does not override equals/hashCode, so two
         * [CompilerConfigurationKey.create] calls with the same name produce unequal keys).
         */
        val OPTIONS_KEY: CompilerConfigurationKey<Map<String, String>> =
            CompilerConfigurationKey.create("io.github.santimattius.structured-coroutines.options")
    }

    private val options: Map<String, String> = configuration.get(OPTIONS_KEY) ?: emptyMap()

    val globalScopeUsage: Severity = getSeverity("globalScopeUsage", Severity.ERROR)
    val inlineCoroutineScope: Severity = getSeverity("inlineCoroutineScope", Severity.ERROR)
    val unstructuredLaunch: Severity = getSeverity("unstructuredLaunch", Severity.ERROR)
    val runBlockingInSuspend: Severity = getSeverity("runBlockingInSuspend", Severity.ERROR)
    val jobInBuilderContext: Severity = getSeverity("jobInBuilderContext", Severity.ERROR)
    val dispatchersUnconfined: Severity = getSeverity("dispatchersUnconfined", Severity.WARNING)
    val cancellationExceptionSubclass: Severity = getSeverity("cancellationExceptionSubclass", Severity.ERROR)
    val suspendInFinally: Severity = getSeverity("suspendInFinally", Severity.WARNING)
    val cancellationExceptionSwallowed: Severity = getSeverity("cancellationExceptionSwallowed", Severity.WARNING)
    val unusedDeferred: Severity = getSeverity("unusedDeferred", Severity.ERROR)
    val redundantLaunchInCoroutineScope: Severity = getSeverity("redundantLaunchInCoroutineScope", Severity.WARNING)
    val loopWithoutYield: Severity = getSeverity("loopWithoutYield", Severity.WARNING)
    val suspendCoroutineWithoutCancellation: Severity = getSeverity("suspendCoroutineWithoutCancellation", Severity.ERROR)
    val callbackFlowWithoutAwaitClose: Severity = getSeverity("callbackFlowWithoutAwaitClose", Severity.ERROR)

    private fun getSeverity(key: String, defaultSeverity: Severity): Severity =
        when (options[key]?.lowercase()) {
            "error" -> Severity.ERROR
            "warning" -> Severity.WARNING
            else -> defaultSeverity
        }
}
