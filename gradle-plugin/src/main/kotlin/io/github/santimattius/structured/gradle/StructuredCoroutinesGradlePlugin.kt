package io.github.santimattius.structured.gradle

import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

/**
 * Gradle plugin that integrates the Structured Coroutines compiler plugin.
 *
 * This plugin:
 * - Resolves the compiler plugin JAR artifact
 * - Passes it to the Kotlin compiler via -Xplugin
 *
 * Usage in build.gradle.kts:
 * ```
 * plugins {
 *     id("io.github.santimattius.structured-coroutines")
 * }
 * ```
 */
class StructuredCoroutinesGradlePlugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        const val PLUGIN_ID = "io.github.santimattius.structured-coroutines"
        const val COMPILER_PLUGIN_ID = "io.github.santimattius.structured-coroutines"
        
        // These should match the published artifact coordinates
        const val GROUP_ID = "io.github.santimattius"
        const val ARTIFACT_ID = "structured-coroutines-compiler"
        const val VERSION = "0.2.0"
    }

    override fun apply(target: Project) {
        // Register the extension with default values
        val extension = target.extensions.create(
            "structuredCoroutines",
            StructuredCoroutinesExtension::class.java
        )
        
        // Set default severities
        extension.globalScopeUsage.convention("error")
        extension.inlineCoroutineScope.convention("error")
        extension.unstructuredLaunch.convention("error")
        extension.runBlockingInSuspend.convention("error")
        extension.jobInBuilderContext.convention("error")
        extension.dispatchersUnconfined.convention("warning")
        extension.cancellationExceptionSubclass.convention("error")
        extension.suspendInFinally.convention("warning")
        extension.cancellationExceptionSwallowed.convention("warning")
        extension.unusedDeferred.convention("error")
        extension.redundantLaunchInCoroutineScope.convention("warning")
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        // Apply to all Kotlin compilations
        return true
    }

    override fun getCompilerPluginId(): String = COMPILER_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(
            groupId = GROUP_ID,
            artifactId = ARTIFACT_ID,
            version = VERSION
        )
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        return kotlinCompilation.target.project.provider {
            val extension = kotlinCompilation.target.project.extensions.findByType(
                StructuredCoroutinesExtension::class.java
            ) ?: return@provider emptyList()
            
            buildList {
                // Pass severity configuration for each rule
                add(SubpluginOption("globalScopeUsage", extension.globalScopeUsage.get()))
                add(SubpluginOption("inlineCoroutineScope", extension.inlineCoroutineScope.get()))
                add(SubpluginOption("unstructuredLaunch", extension.unstructuredLaunch.get()))
                add(SubpluginOption("runBlockingInSuspend", extension.runBlockingInSuspend.get()))
                add(SubpluginOption("jobInBuilderContext", extension.jobInBuilderContext.get()))
                add(SubpluginOption("dispatchersUnconfined", extension.dispatchersUnconfined.get()))
                add(SubpluginOption("cancellationExceptionSubclass", extension.cancellationExceptionSubclass.get()))
                add(SubpluginOption("suspendInFinally", extension.suspendInFinally.get()))
                add(SubpluginOption("cancellationExceptionSwallowed", extension.cancellationExceptionSwallowed.get()))
                add(SubpluginOption("unusedDeferred", extension.unusedDeferred.get()))
                add(SubpluginOption("redundantLaunchInCoroutineScope", extension.redundantLaunchInCoroutineScope.get()))
            }
        }
    }
}
