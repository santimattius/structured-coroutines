package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

/**
 * Main entry point for the Structured Coroutines Kotlin Compiler Plugin.
 *
 * This registrar:
 * - Supports only K2 (FIR) compiler
 * - Registers FIR extensions for semantic analysis of coroutine usage patterns
 *
 * The plugin ID is used by Gradle to identify and configure this plugin.
 */
@OptIn(ExperimentalCompilerApi::class)
class StructuredCoroutinesCompilerPluginRegistrar : CompilerPluginRegistrar() {

    override val supportsK2: Boolean = true

    // Plugin ID for identification by the Kotlin compiler and Gradle
    override val pluginId: String = "io.github.santimattius.structured-coroutines"

    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        FirExtensionRegistrarAdapter.registerExtension(ScoroutinesFirExtensionRegistrar())
    }
}
