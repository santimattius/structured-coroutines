package io.github.santimattius.structured.compiler

import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

/**
 * FIR Extension Registrar for the Structured Coroutines compiler plugin.
 *
 * This registrar is responsible for configuring and registering all FIR extensions
 * needed by the plugin. It registers the [ScoroutinesCallCheckerExtension] which
 * provides additional semantic checks for coroutine usage patterns.
 */
class ScoroutinesFirExtensionRegistrar : FirExtensionRegistrar() {

    override fun ExtensionRegistrarContext.configurePlugin() {
        // Register the additional checkers extension using factory pattern
        +::ScoroutinesCallCheckerExtension
    }
}
