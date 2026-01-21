/**
 * Copyright 2024 Santiago Mattiauda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.diagnostics.Severity

/**
 * Configuration for the Structured Coroutines compiler plugin.
 *
 * This class reads configuration options from [CompilerConfiguration] and provides
 * severity levels for each rule. Rules can be configured to report as ERROR or WARNING.
 *
 * ## Configuration Keys
 *
 * Configuration is passed via compiler plugin options with keys:
 * - `globalScopeUsage`
 * - `inlineCoroutineScope`
 * - `unstructuredLaunch`
 * - `runBlockingInSuspend`
 * - `jobInBuilderContext`
 * - `dispatchersUnconfined`
 * - `cancellationExceptionSubclass`
 * - `suspendInFinally`
 * - `cancellationExceptionSwallowed`
 * - `unusedDeferred`
 * - `redundantLaunchInCoroutineScope`
 *
 * Values can be `"error"` or `"warning"` (case-insensitive).
 *
 * ## Default Severities
 *
 * - Most rules default to ERROR
 * - Some rules default to WARNING (e.g., `dispatchersUnconfined`, `suspendInFinally`)
 */
class PluginConfiguration(configuration: CompilerConfiguration) {
    
    /**
     * Severity for GlobalScope usage rule.
     */
    val globalScopeUsage: Severity = getSeverity(configuration, "globalScopeUsage", Severity.ERROR)
    
    /**
     * Severity for inline CoroutineScope creation rule.
     */
    val inlineCoroutineScope: Severity = getSeverity(configuration, "inlineCoroutineScope", Severity.ERROR)
    
    /**
     * Severity for unstructured launch rule.
     */
    val unstructuredLaunch: Severity = getSeverity(configuration, "unstructuredLaunch", Severity.ERROR)
    
    /**
     * Severity for runBlocking in suspend functions rule.
     */
    val runBlockingInSuspend: Severity = getSeverity(configuration, "runBlockingInSuspend", Severity.ERROR)
    
    /**
     * Severity for Job()/SupervisorJob() in builder context rule.
     */
    val jobInBuilderContext: Severity = getSeverity(configuration, "jobInBuilderContext", Severity.ERROR)
    
    /**
     * Severity for Dispatchers.Unconfined usage rule.
     */
    val dispatchersUnconfined: Severity = getSeverity(configuration, "dispatchersUnconfined", Severity.WARNING)
    
    /**
     * Severity for CancellationException subclass rule.
     */
    val cancellationExceptionSubclass: Severity = getSeverity(configuration, "cancellationExceptionSubclass", Severity.ERROR)
    
    /**
     * Severity for suspend calls in finally without NonCancellable rule.
     */
    val suspendInFinally: Severity = getSeverity(configuration, "suspendInFinally", Severity.WARNING)
    
    /**
     * Severity for catch(Exception) swallowing CancellationException rule.
     */
    val cancellationExceptionSwallowed: Severity = getSeverity(configuration, "cancellationExceptionSwallowed", Severity.WARNING)
    
    /**
     * Severity for unused Deferred (async without await) rule.
     */
    val unusedDeferred: Severity = getSeverity(configuration, "unusedDeferred", Severity.ERROR)
    
    /**
     * Severity for redundant launch in coroutineScope rule.
     */
    val redundantLaunchInCoroutineScope: Severity = getSeverity(configuration, "redundantLaunchInCoroutineScope", Severity.WARNING)
    
    /**
     * Reads a severity option from compiler configuration.
     *
     * Compiler plugin options are passed via SubpluginOption and are stored
     * in the configuration with the format: "plugin:pluginId:key" -> value
     *
     * @param configuration The compiler configuration
     * @param key The configuration key (without plugin prefix)
     * @param defaultSeverity The default severity if not configured
     * @return The configured severity or default
     */
    private fun getSeverity(
        configuration: CompilerConfiguration,
        key: String,
        defaultSeverity: Severity
    ): Severity {
        // SubpluginOption values are stored with the format: "plugin:pluginId:key"
        val pluginId = "io.github.santimattius.structured-coroutines"
        val fullKey = "plugin:$pluginId:$key"
        
        // Try to get the option value from configuration
        // Options are stored as strings
        val optionValue = try {
            val configKey = org.jetbrains.kotlin.config.CompilerConfigurationKey.create<String>(fullKey)
            configuration.get(configKey)
        } catch (e: Exception) {
            null
        } ?: return defaultSeverity
        
        return when (optionValue.lowercase()) {
            "error" -> Severity.ERROR
            "warning" -> Severity.WARNING
            else -> defaultSeverity
        }
    }
}
