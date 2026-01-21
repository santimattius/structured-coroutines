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

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * Main entry point for the Structured Coroutines K2 Compiler Plugin.
 *
 * This plugin enforces structured concurrency best practices at compile time by analyzing
 * FIR (Frontend Intermediate Representation) and emitting errors or warnings for violations.
 *
 * ## Features
 *
 * The plugin detects and reports:
 *
 * ### Errors (Block Compilation)
 * - GlobalScope usage
 * - Inline CoroutineScope creation
 * - Unstructured launch/async (missing @StructuredScope)
 * - runBlocking inside suspend functions
 * - Job()/SupervisorJob() passed to coroutine builders
 * - Classes extending CancellationException
 *
 * ### Warnings
 * - Dispatchers.Unconfined usage
 * - Suspend calls in finally without NonCancellable
 * - catch(Exception) that may swallow CancellationException
 *
 * ## Requirements
 *
 * - Kotlin 2.0+ (K2 compiler)
 * - kotlinx-coroutines-core
 *
 * ## Usage
 *
 * The plugin is automatically applied via the Gradle plugin:
 *
 * ```kotlin
 * plugins {
 *     id("io.github.santimattius.structured-coroutines") version "x.y.z"
 * }
 * ```
 *
 * Or manually via compiler arguments:
 *
 * ```bash
 * kotlinc -Xplugin=structured-coroutines-compiler.jar ...
 * ```
 *
 * ## Architecture
 *
 * ```
 * StructuredCoroutinesCompilerPluginRegistrar
 *     └── ScoroutinesFirExtensionRegistrar
 *             └── ScoroutinesCallCheckerExtension
 *                     ├── UnstructuredLaunchChecker
 *                     ├── RunBlockingInSuspendChecker
 *                     ├── JobInBuilderContextChecker
 *                     ├── DispatchersUnconfinedChecker
 *                     ├── SuspendInFinallyChecker
 *                     ├── CancellationExceptionSwallowedChecker
 *                     └── CancellationExceptionSubclassChecker
 * ```
 *
 * @see ScoroutinesFirExtensionRegistrar
 * @see ScoroutinesCallCheckerExtension
 * @see <a href="https://kotlinlang.org/docs/ksp-compiler-plugins.html">Kotlin Compiler Plugins</a>
 */
@OptIn(ExperimentalCompilerApi::class)
class StructuredCoroutinesCompilerPluginRegistrar : CompilerPluginRegistrar() {

    /**
     * Unique identifier for this compiler plugin.
     * Used by the Kotlin compiler to identify and load the plugin.
     */
    override val supportsK2: Boolean = true

    /**
     * The plugin identifier used for registration.
     */
    override val pluginId: String = "io.github.santimattius.structured-coroutines"

    /**
     * Registers the FIR extension when the plugin is loaded.
     *
     * @param configuration The compiler configuration
     */
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val pluginConfig = PluginConfiguration(configuration)
        FirExtensionRegistrarAdapter.registerExtension(ScoroutinesFirExtensionRegistrar(pluginConfig))
    }
}
