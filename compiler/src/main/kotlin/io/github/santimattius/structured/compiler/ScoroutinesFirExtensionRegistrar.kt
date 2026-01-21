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

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * FIR Extension Registrar for the Structured Coroutines plugin.
 *
 * This class is responsible for registering FIR extensions with the Kotlin compiler.
 * It acts as the bridge between the [StructuredCoroutinesCompilerPluginRegistrar] and
 * the actual FIR analysis extensions.
 *
 * ## Registration Flow
 *
 * ```
 * CompilerPluginRegistrar.registerExtensions()
 *     └── FirExtensionRegistrarAdapter.registerExtension(ScoroutinesFirExtensionRegistrar)
 *             └── ScoroutinesFirExtensionRegistrar.configurePlugin()
 *                     └── +::ScoroutinesCallCheckerExtension
 * ```
 *
 * ## Responsibilities
 *
 * - Registers [ScoroutinesCallCheckerExtension] which provides all the checkers
 * - Runs during FIR (Frontend Intermediate Representation) phase
 * - Enables compile-time analysis of coroutine patterns
 *
 * ## Adding New Extensions
 *
 * To add new FIR extensions (e.g., generators, status transformers):
 *
 * ```kotlin
 * override fun ExtensionRegistrarContext.configurePlugin() {
 *     +::ScoroutinesCallCheckerExtension
 *     +::MyNewExtension  // Add new extensions here
 * }
 * ```
 *
 * @see ScoroutinesCallCheckerExtension
 * @see StructuredCoroutinesCompilerPluginRegistrar
 * @see <a href="https://kotlinlang.org/docs/fir-api-reference.html">FIR API Reference</a>
 */
class ScoroutinesFirExtensionRegistrar(
    private val configuration: PluginConfiguration
) : FirExtensionRegistrar() {

    /**
     * Configures the plugin by registering all FIR extensions.
     *
     * This method is called by the Kotlin compiler during the FIR phase.
     * Extensions registered here will analyze the FIR representation of the code
     * and emit diagnostics for any violations found.
     */
    override fun ExtensionRegistrarContext.configurePlugin() {
        // Store configuration globally so checkers can access it
        PluginConfigurationHolder.configuration = configuration
        
        // Register the main checker extension that provides all coroutine analysis
        +::ScoroutinesCallCheckerExtension
    }
}

/**
 * Holder for plugin configuration to make it accessible to checkers.
 * This is a workaround since FIR extensions are created via function references.
 */
object PluginConfigurationHolder {
    var configuration: PluginConfiguration? = null
}
