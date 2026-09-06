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

import org.jetbrains.kotlin.fir.FirSession
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
 *                     └── +this@ScoroutinesFirExtensionRegistrar::createCallCheckerExtension
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
 *     +this@ScoroutinesFirExtensionRegistrar::createCallCheckerExtension
 *     +this@ScoroutinesFirExtensionRegistrar::createMyNewExtension  // Add new extensions here
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
     *
     * Registers via a **bound** member reference (#68, ADR-4) rather than a global mutable
     * holder: [configuration] is captured directly by [createCallCheckerExtension], so two
     * modules compiling concurrently in the same Gradle/Kotlin daemon can no longer clobber
     * each other's [PluginConfiguration] — each [ScoroutinesFirExtensionRegistrar] instance
     * (one per compilation, created by [StructuredCoroutinesCompilerPluginRegistrar]) has its
     * own.
     */
    override fun ExtensionRegistrarContext.configurePlugin() {
        +this@ScoroutinesFirExtensionRegistrar::createCallCheckerExtension
    }

    private fun createCallCheckerExtension(session: FirSession): ScoroutinesCallCheckerExtension =
        ScoroutinesCallCheckerExtension(session, configuration)
}
